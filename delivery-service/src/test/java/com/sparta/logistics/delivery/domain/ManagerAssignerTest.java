package com.sparta.logistics.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자동 배정 규칙(SA 6.3): 담당 건수 최소, 동점이면 userId 순, 같은 배송 안의 앞선 배정도 센다. */
class ManagerAssignerTest {

    private static final UUID HUB = UUID.randomUUID();
    private static final UUID SMALLER = UUID.fromString("10000000-0000-0000-0000-000000000000");
    /** 부호 있는 long 비교(UUID.compareTo)로는 SMALLER보다 작지만 userId 순으로는 크다 */
    private static final UUID LARGER = UUID.fromString("f0000000-0000-0000-0000-000000000000");

    @Test
    @DisplayName("담당 건수가 가장 적은 후보를 고른다")
    void leastLoaded() {
        ManagerAssigner assigner = new ManagerAssigner(Map.of(HUB, List.of(SMALLER, LARGER)), Map.of(SMALLER, 3L, LARGER, 1L));

        assertThat(assigner.assign(HUB)).isEqualTo(LARGER);
    }

    @Test
    @DisplayName("동점이면 userId 문자열 순으로 앞선 후보를 고르고, 고른 사람의 건수를 올려 다음 구간은 다른 후보가 받는다")
    void tieByUserIdAndCountsEarlierAssignment() {
        ManagerAssigner assigner = new ManagerAssigner(Map.of(HUB, List.of(LARGER, SMALLER)), Map.of());

        assertThat(assigner.assign(HUB)).isEqualTo(SMALLER);
        assertThat(assigner.assign(HUB)).isEqualTo(LARGER);
    }

    @Test
    @DisplayName("구간 담당 허브에 후보가 없으면 DELIVERY_MANAGER_NOT_FOUND")
    void noCandidate() {
        ManagerAssigner assigner = new ManagerAssigner(Map.of(HUB, List.of(SMALLER)), Map.of());

        assertThatThrownBy(() -> assigner.assign(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND);
    }
}
