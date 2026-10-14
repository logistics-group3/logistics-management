package com.sparta.logistics.order.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Slack 알림 전용 스레드 풀 (ADR-14). 크기·큐를 제한해 알림이 몰려도 다른 비동기 작업을 막지 않는다.
 * 리스너에서 {@code @Async(AsyncConfig.SLACK_EXECUTOR)}로 지정한다.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    public static final String SLACK_EXECUTOR = "slackExecutor";

    @Bean(SLACK_EXECUTOR)
    public Executor slackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("slack-");
        // 큐가 가득 차면 알림을 버린다. 알림은 부가 기능이라 주문 스레드를 막지 않는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
