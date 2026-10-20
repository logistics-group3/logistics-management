package com.sparta.logistics.delivery.infrastructure.config;

import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.assignment.AssignmentTrigger;
import com.sparta.logistics.delivery.application.assignment.ManagerAssignmentService;
import com.sparta.logistics.delivery.infrastructure.assignment.InProcessAssignmentDispatcher;
import com.sparta.logistics.delivery.infrastructure.assignment.PendingAssignmentPoller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 구간 담당자 비동기 배정 구성(결정 로그 27).
 * - delivery.assignment.async: false면 배정을 부른 스레드에서 바로 실행한다(테스트에서 생성 직후 배정 결과를 보려고).
 * - delivery.assignment.poll-enabled: 주기 작업을 켜고 끈다. 간격은 delivery.assignment.poll-interval-ms(기본 30초).
 * - delivery.assignment.batch-window-ms: 실행 중에 온 신호를 모았다가 다시 돌기 전 기다리는 시간(기본 0, 바로 다시 돈다).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
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
}
