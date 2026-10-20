package com.sparta.logistics.delivery.infrastructure.assignment;

import com.sparta.logistics.delivery.application.assignment.AssignmentResult;
import com.sparta.logistics.delivery.application.assignment.AssignmentTrigger;
import com.sparta.logistics.delivery.application.assignment.ManagerAssignmentService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

/**
 * 같은 프로세스 안에서 허브 배정을 비동기로 실행한다(결정 로그 27).
 *
 * Note: 신호를 허브마다 하나로 합친다. 허브마다 "실행 중"과 "끝나면 한 번 더" 두 칸만 둔다. 실행 중에 온 신호는 표시만 남기고,
 * 실행이 끝날 때 표시가 있으면 한 번 더 돈다. 그래서 주문이 몰려도 허브당 배정 작업은 동시에 하나이고, 한 번 돌 때 그동안 쌓인
 * 대기 구간을 모두 처리한다. 실행기(스레드 풀)가 가득 차 거절되면 신호를 버린다. 대기 구간은 DB에 남아 주기 작업이 줍는다.
 * 주문 요청 스레드가 배정을 대신 실행하지 않는다(몰림이 요청 경로로 돌아오지 않게).
 *
 * Note: 배치 창. 실행 중에 신호가 와서 다시 돌 때 batchWindowMillis만큼 기다렸다 돌아, 그동안 쌓인 대기 구간을 한 번에 처리한다.
 * 기본은 0이다. 창이 없어도 배정이 느려지면 그동안 온 신호가 다음 한 번에 묶여(생성 640/초에서 1회 약 56구간) 생성이 버텼다.
 * 창 200ms는 신호가 겹치는 부하에서 배정 실행을 약 1/30, DB CPU를 약 절반으로 줄이는 대신 배정 지연을 창만큼 늘린다(결정 로그 27).
 * 첫 신호와 한 번에 처리할 양을 채워 더 남은 경우(hasMore)는 기다리지 않는다.
 */
@Slf4j
public class InProcessAssignmentDispatcher implements AssignmentTrigger {

    /** 한 번 깨운 허브를 연달아 다시 도는 최대 횟수. 그래도 남은 대기는 주기 작업이 줍는다. */
    static final int MAX_ROUNDS = 20;

    private final ManagerAssignmentService assignmentService;
    private final Executor executor;
    private final long batchWindowMillis;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rerun = ConcurrentHashMap.newKeySet();

    /**
     * @param executor          배정 전용 실행기. {@code Runnable::run}이면 부른 스레드에서 바로 실행한다(테스트용 동기 모드)
     * @param batchWindowMillis 다시 돌기 전에 신호를 모으는 시간. 0이면 바로 다시 돈다
     */
    public InProcessAssignmentDispatcher(ManagerAssignmentService assignmentService, Executor executor,
                                         long batchWindowMillis) {
        this.assignmentService = assignmentService;
        this.executor = executor;
        this.batchWindowMillis = batchWindowMillis;
    }

    @Override
    public void request(Collection<UUID> hubIds) {
        hubIds.forEach(this::request);
    }

    private void request(UUID hubId) {
        if (running.add(hubId)) {
            submit(hubId);
            return;
        }
        rerun.add(hubId);
        // 실행 중이던 작업이 방금 끝나 "한 번 더" 표시를 보지 못했을 수 있다. 그 사이 비었으면 직접 시작한다.
        if (running.add(hubId)) {
            rerun.remove(hubId);
            submit(hubId);
        }
    }

    private void submit(UUID hubId) {
        try {
            executor.execute(() -> drain(hubId));
        } catch (RejectedExecutionException e) {
            // Spring TaskRejectedException도 이 예외의 하위 타입이다
            running.remove(hubId);
            log.warn("배정 신호 버림: hubId={}, reason=EXECUTOR_FULL", hubId);
        }
    }

    private void drain(UUID hubId) {
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                rerun.remove(hubId);
                AssignmentResult result = runOnce(hubId);
                if (result == null || result.outcome() != AssignmentResult.Outcome.ASSIGNED) {
                    break;
                }
                if (result.hasMore()) {
                    continue;
                }
                if (!rerun.contains(hubId) || !waitBatchWindow()) {
                    break;
                }
            }
        } finally {
            running.remove(hubId);
            if (rerun.remove(hubId)) {
                request(hubId);
            }
        }
    }

    /** 배치 창만큼 기다린다. 기다리는 동안 온 신호는 다음 실행 한 번에 묶인다. 중단되면 false. */
    private boolean waitBatchWindow() {
        if (batchWindowMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(batchWindowMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private AssignmentResult runOnce(UUID hubId) {
        try {
            AssignmentResult result = assignmentService.assignPending(hubId);
            if (result.assignedRoutes() > 0) {
                log.info("구간 배정: hubId={}, routes={}, completedDeliveries={}, hasMore={}",
                        hubId, result.assignedRoutes(), result.completed().size(), result.hasMore());
            }
            return result;
        } catch (RuntimeException e) {
            // user I-14 장애 등. 대기 구간은 그대로 남고 주기 작업이 다시 시도한다.
            log.warn("구간 배정 실패: hubId={}, reason={}", hubId, e.getClass().getSimpleName());
            return null;
        }
    }
}
