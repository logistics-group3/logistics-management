package com.sparta.logistics.delivery.infrastructure.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.sparta.logistics.delivery.application.assignment.AssignmentResult;
import com.sparta.logistics.delivery.application.assignment.ManagerAssignmentService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신호 합치기(결정 로그 27): 허브마다 실행 중 하나 + "끝나면 한 번 더" 표시 하나 */
class InProcessAssignmentDispatcherTest {

    private static final UUID HUB = UUID.randomUUID();
    private static final AssignmentResult DONE = new AssignmentResult(AssignmentResult.Outcome.ASSIGNED, 1, List.of(), false);
    private static final AssignmentResult MORE = new AssignmentResult(AssignmentResult.Outcome.ASSIGNED, 1, List.of(), true);
    private static final AssignmentResult BUSY = new AssignmentResult(AssignmentResult.Outcome.BUSY, 0, List.of(), false);

    private final ManagerAssignmentService service = mock(ManagerAssignmentService.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("실행 중에 같은 허브 신호가 50번 와도 끝난 뒤 한 번만 더 돈다")
    void coalescesSignalsWhileRunning() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch secondDone = new CountDownLatch(1);
        given(service.assignPending(HUB)).willAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                release.await(10, TimeUnit.SECONDS);
            } else {
                secondDone.countDown();
            }
            return DONE;
        });
        InProcessAssignmentDispatcher dispatcher = new InProcessAssignmentDispatcher(service, executor, 0);

        dispatcher.request(Set.of(HUB));
        assertThat(firstStarted.await(10, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 50; i++) {
            dispatcher.request(Set.of(HUB));
        }
        release.countDown();

        assertThat(secondDone.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 번에 다 못 하면(hasMore) 이어서 돌고, 다른 작업이 처리 중(BUSY)이면 멈춘다")
    void continuesWhileMoreAndStopsWhenBusy() {
        given(service.assignPending(HUB)).willReturn(MORE, MORE, BUSY);
        InProcessAssignmentDispatcher dispatcher = new InProcessAssignmentDispatcher(service, Runnable::run, 0);

        dispatcher.request(Set.of(HUB));

        then(service).should(times(3)).assignPending(HUB);
    }

    @Test
    @DisplayName("실행기가 가득 차 거절하면 신호를 버리고, 다음 신호에서는 다시 실행한다")
    void dropsSignalWhenExecutorFull() {
        AtomicInteger submitted = new AtomicInteger();
        InProcessAssignmentDispatcher dispatcher = new InProcessAssignmentDispatcher(service, task -> {
            if (submitted.incrementAndGet() == 1) {
                throw new RejectedExecutionException("full");
            }
            task.run();
        }, 0);
        given(service.assignPending(HUB)).willReturn(DONE);

        dispatcher.request(Set.of(HUB));
        then(service).should(times(0)).assignPending(HUB);

        dispatcher.request(Set.of(HUB));
        then(service).should(times(1)).assignPending(HUB);
    }

    @Test
    @DisplayName("배정이 실패해도(user 장애 등) 예외를 올리지 않고 다음 신호를 받을 수 있다")
    void survivesFailure() {
        given(service.assignPending(HUB)).willThrow(new IllegalStateException("user down")).willReturn(DONE);
        InProcessAssignmentDispatcher dispatcher = new InProcessAssignmentDispatcher(service, Runnable::run, 0);

        dispatcher.request(Set.of(HUB));
        dispatcher.request(Set.of(HUB));

        then(service).should(times(2)).assignPending(HUB);
    }

    @Test
    @DisplayName("실행 중에 신호가 오면 배치 창만큼 기다렸다가 그동안 온 신호를 한 번에 처리한다. 첫 신호는 기다리지 않는다")
    void waitsBatchWindowBeforeRerun() {
        long window = 150;
        AtomicInteger calls = new AtomicInteger();
        long[] startedAt = new long[3];
        InProcessAssignmentDispatcher[] holder = new InProcessAssignmentDispatcher[1];
        given(service.assignPending(HUB)).willAnswer(invocation -> {
            int call = calls.incrementAndGet();
            startedAt[call] = System.nanoTime();
            if (call == 1) {
                // 실행 중에 주문 세 건의 신호가 들어온다
                holder[0].request(Set.of(HUB));
                holder[0].request(Set.of(HUB));
                holder[0].request(Set.of(HUB));
            }
            return DONE;
        });
        holder[0] = new InProcessAssignmentDispatcher(service, Runnable::run, window);

        long requestedAt = System.nanoTime();
        holder[0].request(Set.of(HUB));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(TimeUnit.NANOSECONDS.toMillis(startedAt[1] - requestedAt)).isLessThan(window);
        assertThat(TimeUnit.NANOSECONDS.toMillis(startedAt[2] - startedAt[1])).isGreaterThanOrEqualTo(window);
    }
}
