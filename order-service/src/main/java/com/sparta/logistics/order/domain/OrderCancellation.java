package com.sparta.logistics.order.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** O-05의 실행권과 복구 대상을 DB에 남긴다. 주문의 공개 상태와 분리한다(order ADR 0006). */
@Getter
@Entity
@Table(name = "p_order_cancellations")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCancellation extends BaseEntity {

    @Id
    @Column(name = "order_cancellation_id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderCancellationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private CancellationFailureReason failureReason;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    public static OrderCancellation start(UUID id, UUID orderId, UUID deliveryId) {
        if (id == null || orderId == null || deliveryId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        OrderCancellation cancellation = new OrderCancellation();
        cancellation.id = id;
        cancellation.orderId = orderId;
        cancellation.deliveryId = deliveryId;
        cancellation.status = OrderCancellationStatus.PROCESSING;
        return cancellation;
    }

    public void complete() {
        validateProcessing();
        status = OrderCancellationStatus.COMPLETED;
    }

    public void abort() {
        validateProcessing();
        status = OrderCancellationStatus.ABORTED;
    }

    public void requireRecovery(CancellationFailureReason reason) {
        validateProcessing();
        if (reason == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        status = OrderCancellationStatus.RECOVERY_REQUIRED;
        failureReason = reason;
    }

    public void validateProcessing() {
        if (status != OrderCancellationStatus.PROCESSING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
