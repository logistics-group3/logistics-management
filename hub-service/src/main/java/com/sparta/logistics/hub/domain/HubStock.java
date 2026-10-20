package com.sparta.logistics.hub.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** 허브별 상품 재고. 허브 × 상품당 1행이고 수량은 0 이상이다(SA 5.3). */
@Getter
@Entity
@Table(name = "p_hub_stocks")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubStock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hub_stock_id")
    private UUID id;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "hub_id", nullable = false, updatable = false)
    private UUID hubId;

    /** company-service 상품 ID. 서비스 간 참조라 FK가 없다. */
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    private HubStock(UUID hubId, UUID productId) {
        this.hubId = hubId;
        this.productId = productId;
        this.quantity = 0;
    }

    public static HubStock create(UUID hubId, UUID productId) {
        if (hubId == null || productId == null) {
            throw invalid("허브와 상품은 필수입니다.");
        }
        return new HubStock(hubId, productId);
    }

    /** 입고·복원. 수량은 1 이상이어야 한다. */
    public void increase(int amount) {
        requirePositive(amount);
        // Note: int 범위를 넘기면 음수가 되어 DB CHECK 위반(500)으로 드러나므로 도메인에서 400으로 막는다.
        try {
            this.quantity = Math.addExact(this.quantity, amount);
        } catch (ArithmeticException e) {
            throw invalid("재고 수량이 허용 범위를 넘습니다.");
        }
    }

    /** 차감. 남은 수량보다 많이 빼면 STOCK_INSUFFICIENT(409)이고 수량은 바뀌지 않는다. */
    public void decrease(int amount) {
        requirePositive(amount);
        if (amount > quantity) {
            throw new BusinessException(HubErrorCode.STOCK_INSUFFICIENT);
        }
        this.quantity -= amount;
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw invalid("수량은 1 이상이어야 합니다.");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST, message);
    }
}
