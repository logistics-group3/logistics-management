package com.sparta.logistics.delivery.infrastructure.config;

import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.assignment.AssignedDelivery;
import com.sparta.logistics.delivery.application.assignment.AssignmentNotifier;
import com.sparta.logistics.delivery.application.assignment.AssignmentTrigger;
import com.sparta.logistics.delivery.application.assignment.ConfirmationCheckPolicy;
import com.sparta.logistics.delivery.application.assignment.DeliveryConfirmationService;
import com.sparta.logistics.delivery.application.assignment.ManagerAssignmentService;
import com.sparta.logistics.delivery.application.assignment.ManualAssignmentPolicy;
import com.sparta.logistics.delivery.infrastructure.assignment.InProcessAssignmentDispatcher;
import com.sparta.logistics.delivery.infrastructure.assignment.PendingAssignmentPoller;
import com.sparta.logistics.delivery.infrastructure.assignment.UnconfirmedDeliveryPoller;
import com.sparta.logistics.delivery.infrastructure.client.OrderAssignmentNotifier;
import com.sparta.logistics.delivery.infrastructure.client.OrderClient;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 구간 담당자 비동기 배정 구성(결정 로그 27).
 * - delivery.assignment.async: false면 배정을 부른 스레드에서 바로 실행한다(테스트에서 생성 직후 배정 결과를 보려고).
 * - delivery.assignment.poll-enabled: 주기 작업을 켜고 끈다. 간격은 delivery.assignment.poll-interval-ms(기본 30초).
 * - delivery.assignment.batch-window-ms: 실행 중에 온 신호를 모았다가 다시 돌기 전 기다리는 시간(기본 0, 바로 다시 돈다).
 * - delivery.confirmation.*: 주문 확정 뒤 배정, 수동 지정 기준, order 알림, 확정 유실 안전망(결정 로그 29, ConfirmationProperties).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ConfirmationProperties.class)
public class ManagerAssignmentConfig {

    /**
     * 배정 전용 실행기. 허브당 작업이 하나라 스레드 2개면 충분하다. 배정 작업은 스레드 수만큼만 DB 커넥션을 쓴다.
     * Note: 포화 시 거절(AbortPolicy)하고 디스패처가 신호를 버린다. 요청 스레드에서 대신 실행하지 않는다.
     */
    @Bean
    public ThreadPoolTaskExecutor managerAssignmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("manager-assignment-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public AssignmentTrigger assignmentTrigger(ManagerAssignmentService assignmentService,
                                               ThreadPoolTaskExecutor managerAssignmentExecutor,
                                               @Value("${delivery.assignment.async:true}") boolean async,
                                               @Value("${delivery.assignment.batch-window-ms:0}") long batchWindowMillis) {
        return new InProcessAssignmentDispatcher(assignmentService, async ? managerAssignmentExecutor : Runnable::run,
                batchWindowMillis);
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.assignment.poll-enabled", havingValue = "true", matchIfMissing = true)
    public PendingAssignmentPoller pendingAssignmentPoller(DeliveryQueryRepository deliveryQueryRepository,
                                                           AssignmentTrigger assignmentTrigger) {
        return new PendingAssignmentPoller(deliveryQueryRepository, assignmentTrigger);
    }

    @Bean
    public ManualAssignmentPolicy manualAssignmentPolicy(ConfirmationProperties properties) {
        return new ManualAssignmentPolicy(properties.manualAssignmentAfter());
    }

    @Bean
    public ConfirmationCheckPolicy confirmationCheckPolicy(ConfirmationProperties properties) {
        ConfirmationProperties.Check check = properties.check();
        return new ConfirmationCheckPolicy(check.after(), check.giveUpAfter(), check.batchSize());
    }

    /**
     * order 알림(I-20) 전용 실행기. 배정 스레드가 order 응답을 기다리지 않게 따로 둔다.
     * Note: 생성 640/초면 알림도 초당 640건이다. 호출이 수 ms면 스레드 4개로 충분하고, order가 멈추면 대기열이 차서 버린다(기록만).
     */
    @Bean
    public ThreadPoolTaskExecutor assignmentNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("assignment-notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public AssignmentNotifier assignmentNotifier(ConfirmationProperties properties, OrderClient orderClient,
                                                 ThreadPoolTaskExecutor assignmentNotificationExecutor) {
        if (!properties.notifyOrder()) {
            return new AssignmentNotifier() {
                @Override
                public void notifyAssigned(Collection<AssignedDelivery> deliveries) {
                }

                @Override
                public void notifyManualRequired(Collection<AssignedDelivery> deliveries) {
                }
            };
        }
        return new OrderAssignmentNotifier(orderClient, assignmentNotificationExecutor);
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.confirmation.check.enabled", havingValue = "true", matchIfMissing = true)
    public UnconfirmedDeliveryPoller unconfirmedDeliveryPoller(DeliveryConfirmationService deliveryConfirmationService) {
        return new UnconfirmedDeliveryPoller(deliveryConfirmationService);
    }
}
