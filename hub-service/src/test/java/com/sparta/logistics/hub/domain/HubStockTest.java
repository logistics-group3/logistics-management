package com.sparta.logistics.hub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HubStockTest {

    private final UUID hubId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    @DisplayName("재고는 수량 0으로 만들어진다")
    void create() {
        HubStock stock = HubStock.create(hubId, productId);

        assertThat(stock.getQuantity()).isZero();
        assertThat(stock.getHubId()).isEqualTo(hubId);
        assertThat(stock.getProductId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("허브나 상품이 없으면 만들 수 없다")
    void createWithoutReference() {
        assertThatThrownBy(() -> HubStock.create(null, productId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> HubStock.create(hubId, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("입고하면 수량이 늘어난다")
    void increase() {
        HubStock stock = HubStock.create(hubId, productId);

        stock.increase(500);
        stock.increase(20);

        assertThat(stock.getQuantity()).isEqualTo(520);
    }

    @Test
    @DisplayName("입고 결과가 int 범위를 넘으면 INVALID_REQUEST이고 수량은 그대로다")
    void increaseOverflow() {
        HubStock stock = HubStock.create(hubId, productId);
        stock.increase(Integer.MAX_VALUE);

        assertThatThrownBy(() -> stock.increase(1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        assertThat(stock.getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("남은 수량 이하로 차감하면 수량이 줄고 전부 차감할 수 있다")
    void decrease() {
        HubStock stock = HubStock.create(hubId, productId);
        stock.increase(10);

        stock.decrease(4);
        stock.decrease(6);

        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("남은 수량보다 많이 차감하면 STOCK_INSUFFICIENT이고 수량은 그대로다")
    void decreaseInsufficient() {
        HubStock stock = HubStock.create(hubId, productId);
        stock.increase(5);

        assertThatThrownBy(() -> stock.decrease(6))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.STOCK_INSUFFICIENT));
        assertThat(stock.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("0 이하 수량으로는 입고·차감할 수 없다")
    void nonPositiveAmount() {
        HubStock stock = HubStock.create(hubId, productId);
        stock.increase(5);

        assertThatThrownBy(() -> stock.increase(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stock.increase(-1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stock.decrease(0)).isInstanceOf(BusinessException.class);
        assertThat(stock.getQuantity()).isEqualTo(5);
    }
}
