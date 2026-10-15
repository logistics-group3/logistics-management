package com.sparta.logistics.order.infrastructure.config;

import com.sparta.logistics.order.infrastructure.notification.SlackOrderNotificationSender;
import com.sparta.logistics.order.infrastructure.notification.SlackProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(SlackProperties.class)
public class OrderNotificationConfig {

    @Bean
    public ThreadPoolTaskExecutor orderNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("order-notification-");
        // Note: 포화 시 기본 AbortPolicy로 거부하고 리스너가 기록한다. 요청 스레드에서 대신 실행하지 않는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public SlackOrderNotificationSender slackOrderNotificationSender(SlackProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        // Note: 공통 Feign의 사용자 헤더와 재시도를 쓰지 않는다. Webhook POST는 중복 발송 위험이 있다.
        RestClient client = RestClient.builder().requestFactory(factory).build();
        return new SlackOrderNotificationSender(client, properties);
    }
}
