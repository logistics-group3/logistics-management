package com.sparta.logistics.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 재고 변경 이력(SA 5.3). 기록형 데이터라 수정·삭제가 없어 BaseEntity 대신 생성 Audit 두 컬럼만 둔다.
 * Note: (hubStockId, orderId, changeType) 유니크 인덱스가 같은 주문의 중복 차감·복원을 DB 수준에서 막는 멱등키다.
 */
@Getter
@Entity
@Table(name = "p_hub_stock_histories")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubStockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hub_stock_history_id")
    private UUID id;

    @Column(name = "hub_stock_id", nullable = false, updatable = false)
    private UUID hubStockId;

    /** 입고는 null. 주문 ID는 order-service 참조라 FK가 없다. */
    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 20)
    private StockChangeType changeType;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "quantity_after", nullable = false, updatable = false)
    private int quantityAfter;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    private HubStockHistory(UUID hubStockId, UUID orderId, StockChangeType changeType, int quantity, int quantityAfter) {
        this.hubStockId = hubStockId;
        this.orderId = orderId;
        this.changeType = changeType;
        this.quantity = quantity;
        this.quantityAfter = quantityAfter;
    }

    /** 변경이 끝난 재고(quantityAfter가 반영된 상태)를 기준으로 이력을 만든다. */
    public static HubStockHistory inbound(HubStock stock, int quantity) {
        return new HubStockHistory(stock.getId(), null, StockChangeType.INBOUND, quantity, stock.getQuantity());
    }

    public static HubStockHistory decrease(HubStock stock, UUID orderId, int quantity) {
        return new HubStockHistory(stock.getId(), orderId, StockChangeType.DECREASE, quantity, stock.getQuantity());
    }

    public static HubStockHistory restore(HubStock stock, UUID orderId, int quantity) {
        return new HubStockHistory(stock.getId(), orderId, StockChangeType.RESTORE, quantity, stock.getQuantity());
    }
}
