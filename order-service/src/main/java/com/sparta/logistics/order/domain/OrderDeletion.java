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

/** 삭제 전에 확인한 조건을 보존한다. 배송만 삭제된 뒤에도 같은 삭제를 계속할 수 있다(ADR 0009). */
@Getter
@Entity
@Table(name = "p_order_deletions")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeletion extends BaseEntity {
    @Id
    @Column(name = "order_deletion_id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderDeletionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeletionEligibility eligibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private DeletionFailureReason failureReason;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    public static OrderDeletion start(UUID id, Order order, boolean deliveryCompleted) {
        if (id == null || order == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        order.validateDeletable(deliveryCompleted);
        OrderDeletion deletion = new OrderDeletion();
        deletion.id = id;
        deletion.orderId = order.getId();
        deletion.deliveryId = order.getDeliveryId();
        deletion.eligibility = order.getStatus() == OrderStatus.CANCELED
                ? DeletionEligibility.ORDER_CANCELED : DeletionEligibility.DELIVERY_DELIVERED;
        deletion.status = OrderDeletionStatus.PROCESSING;
        return deletion;
    }

    public void validateResumable(Order order) {
        validateUnfinished();
        if (order == null || !orderId.equals(order.getId()) || !deliveryId.equals(order.getDeliveryId())) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_DELETABLE);
        }
    }

    public void complete() {
        validateUnfinished();
        status = OrderDeletionStatus.COMPLETED;
        failureReason = null;
    }

    public void requireRecovery(DeletionFailureReason reason) {
        validateUnfinished();
        if (reason == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        status = OrderDeletionStatus.RECOVERY_REQUIRED;
        failureReason = reason;
    }

    private void validateUnfinished() {
        if (isDeleted() || status == OrderDeletionStatus.COMPLETED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_DELETABLE);
        }
    }
}
