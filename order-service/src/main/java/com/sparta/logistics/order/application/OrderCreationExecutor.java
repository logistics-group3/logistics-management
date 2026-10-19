package com.sparta.logistics.order.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 접수한 주문 생성의 뒷단(I-09 → 주문 저장 → 보상)을 요청 스레드 밖에서 실행한다(이슈 #16).
 * Note: 스레드 수와 대기열을 모두 제한한다. 하위 서비스가 느려질 때 작업이 무한히 쌓여 메모리·DB 연결을 먹지 않게 하고,
 * 가득 차면 제출이 거절되어 접수 단계가 503으로 빠르게 실패한다. Executor 빈으로 등록하지 않고 이 컴포넌트가 직접 소유한다.
 * 그래야 스프링 부트의 기본 TaskExecutor 구성과 다른 비동기 처리(Slack 알림)에 영향을 주지 않는다.
 */
@Slf4j
@Component
public class OrderCreationExecutor {

    private final Executor executor;
    private final ThreadPoolExecutor pool;

    @Autowired
    public OrderCreationExecutor(
            @Value("${order.creation.executor.threads:4}") int threads,
            @Value("${order.creation.executor.queue-capacity:50}") int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "order-creation-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        this.pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
        this.executor = pool;
    }

    private OrderCreationExecutor(Executor executor) {
        this.executor = executor;
        this.pool = null;
    }

    /** 테스트용. 제출한 스레드에서 바로 실행해 결과를 결정적으로 만든다. */
    public static OrderCreationExecutor direct() {
        return new OrderCreationExecutor(Runnable::run);
    }

    /** 가득 차면 RejectedExecutionException. 호출자가 접수 실패로 처리한다. */
    public void submit(Runnable task) throws RejectedExecutionException {
        executor.execute(task);
    }

    /** 종료 시 진행 중인 생성이 끝나도록 최대 30초 기다린다. 못 끝낸 시도는 PROCESSING으로 남는다(수동 확인 대상). */
    @PreDestroy
    void shutdown() {
        if (pool == null) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("주문 생성 작업이 30초 안에 끝나지 않았다. 남은 시도는 PROCESSING으로 남는다: active={}, queued={}",
                        pool.getActiveCount(), pool.getQueue().size());
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
