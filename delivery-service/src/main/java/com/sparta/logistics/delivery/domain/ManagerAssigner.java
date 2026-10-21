package com.sparta.logistics.delivery.domain;

import com.sparta.logistics.common.exception.BusinessException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 구간 담당자 자동 배정(SA 6.3). 후보 중 담당 건수가 가장 적은 사람을 고르고, 동점이면 userId 순이다.
 * Note: 고를 때마다 고른 사람의 건수를 1 올린다. 같은 배송의 집하·허브 이동은 같은 허브 후보에서 고르므로,
 * 첫 배정을 세지 않으면 두 구간이 늘 같은 사람에게 몰린다(delivery.md 4-2).
 * 이 클래스는 넘겨받은 건수만 본다. 동시 배정은 허브별 배정 작업이 한 번에 하나만 돌게 해서 막는다(ManagerAssignmentService, 결정 로그 27).
 */
public class ManagerAssigner {

    /** userId 순: 문자열 비교. UUID.compareTo는 부호 있는 long 비교라 문자열·DB 정렬 순서와 다르다. */
    private static final Comparator<UUID> USER_ID_ORDER = Comparator.comparing(UUID::toString);

    private final Map<UUID, List<UUID>> candidatesByHub;
    private final Map<UUID, Long> activeRouteCounts;

    /**
     * @param candidatesByHub   구간 담당 허브별 배송담당자 userId(I-14)
     * @param activeRouteCounts 후보별 담당 건수. 없는 후보는 0건이다
     */
    public ManagerAssigner(Map<UUID, List<UUID>> candidatesByHub, Map<UUID, Long> activeRouteCounts) {
        this.candidatesByHub = Map.copyOf(candidatesByHub);
        this.activeRouteCounts = new HashMap<>(activeRouteCounts);
    }

    /** 구간 담당 허브의 후보 중 한 명을 고른다. 후보가 없으면 404 DELIVERY_MANAGER_NOT_FOUND. */
    UUID assign(UUID managingHubId) {
        List<UUID> candidates = candidatesByHub.getOrDefault(managingHubId, List.of());
        UUID chosen = candidates.stream()
                .min(Comparator.<UUID>comparingLong(this::countOf).thenComparing(USER_ID_ORDER))
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND));
        activeRouteCounts.merge(chosen, 1L, Long::sum);
        return chosen;
    }

    private long countOf(UUID managerId) {
        return activeRouteCounts.getOrDefault(managerId, 0L);
    }
}
