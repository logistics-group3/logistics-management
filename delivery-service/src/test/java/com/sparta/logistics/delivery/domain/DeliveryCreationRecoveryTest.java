package com.sparta.logistics.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.DeliveryFixture;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeliveryCreationRecoveryTest {
    @Test
    @DisplayName("집하 대기 배송과 모든 구간을 함께 논리 삭제한다")
    void closesWaitingDelivery() {
        Delivery delivery = DeliveryFixture.twoRoutes();
        delivery.closeCreation(delivery.getId(), "operator");
        assertThat(delivery.isDeleted()).isTrue();
        assertThat(delivery.getRoutes()).allSatisfy(route -> assertThat(route.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("배송이 출발했으면 생성 복구가 배송을 삭제하지 않는다")
    void refusesMovingDelivery() {
        Delivery delivery = DeliveryFixture.twoRoutes();
        delivery.startRoute(delivery.getRoutes().get(0).getId());
        assertThatThrownBy(() -> delivery.closeCreation(delivery.getId(), "operator"))
                .isInstanceOf(BusinessException.class);
        assertThat(delivery.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("취소되거나 관찰한 배송 ID가 다르면 정리하지 않는다")
    void refusesCanceledOrMismatchedDelivery() {
        Delivery delivery = DeliveryFixture.twoRoutes();
        assertThatThrownBy(() -> delivery.closeCreation(UUID.randomUUID(), "operator")).isInstanceOf(BusinessException.class);
        delivery.cancel();
        assertThatThrownBy(() -> delivery.closeCreation(delivery.getId(), "operator")).isInstanceOf(BusinessException.class);
        assertThat(delivery.isDeleted()).isFalse();
    }
}
