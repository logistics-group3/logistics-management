package com.sparta.logistics.delivery.application.assignment;

import java.util.List;

/**
 * 허브 배정 한 번의 결과.
 *
 * @param outcome         실행 결과
 * @param assignedRoutes  이번에 담당자를 넣은 구간 수
 * @param completed       이번 배정으로 배정 대기 구간이 모두 없어진 배송(담당자 알림 대상)
 * @param hasMore         한 번에 처리할 수 있는 양을 채워 대기가 더 남았을 수 있다
 */
public record AssignmentResult(Outcome outcome, int assignedRoutes, List<AssignedDelivery> completed, boolean hasMore) {

    public enum Outcome {
        /** 허브 잠금을 잡고 배정했다(0건일 수 있음) */
        ASSIGNED,
        /** 다른 작업이 이 허브를 처리 중이라 건너뛰었다 */
        BUSY,
        /** 허브에 배송담당자 후보가 없어 대기로 두었다 */
        NO_CANDIDATES
    }

    static AssignmentResult assigned(int assignedRoutes, List<AssignedDelivery> completed, boolean hasMore) {
        return new AssignmentResult(Outcome.ASSIGNED, assignedRoutes, List.copyOf(completed), hasMore);
    }

    static AssignmentResult busy() {
        return new AssignmentResult(Outcome.BUSY, 0, List.of(), false);
    }

    static AssignmentResult noCandidates() {
        return new AssignmentResult(Outcome.NO_CANDIDATES, 0, List.of(), false);
    }
}
