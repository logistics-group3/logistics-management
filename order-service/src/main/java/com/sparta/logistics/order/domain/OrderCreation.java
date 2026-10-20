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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** 주문 행이 만들어지기 전부터 생성 결과를 추적한다(order ADR 0007). */
@Getter
@Entity
@Table(name = "p_order_creations")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreation extends BaseEntity {

    @Id
    @Column(name = "order_creation_id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderCreationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private CreationFailureReason failureReason;

    /** 주문보다 먼저 저장하므로 p_orders에 대한 물리 FK를 만들지 않는다. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "orderer_id", nullable = false)
    private UUID ordererId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "recovery_id")
    private UUID recoveryId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 67)
    private String requestHash;

    /** ABORTED의 원인 코드와 안내 문구. 비동기 접수라 요청 응답으로 돌려줄 수 없어 진행 조회가 보여 준다. */
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", columnDefinition = "jsonb")
    private OrderCreationSnapshot responseSnapshot;

    public static OrderCreation start(UUID id, UUID orderId, UUID ordererId) {
        if (id == null || orderId == null || ordererId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        OrderCreation creation = new OrderCreation();
        creation.id = id;
        creation.orderId = orderId;
        creation.ordererId = ordererId;
        creation.status = OrderCreationStatus.PROCESSING;
        return creation;
    }

    public static OrderCreation start(UUID id, UUID orderId, UUID ordererId, OrderRequestIdentity identity) {
        if (identity == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        OrderCreation creation = start(id, orderId, ordererId);
        creation.idempotencyKey = identity.key();
        creation.requestHash = identity.requestHash();
        return creation;
    }

    public void complete(UUID deliveryId) {
        validateProcessing();
        if (deliveryId == null || idempotencyKey != null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        this.deliveryId = deliveryId;
        status = OrderCreationStatus.COMPLETED;
    }

    public void completeWithSnapshot(Order order) {
        validateProcessing();
        if (order == null || !orderId.equals(order.getId()) || !ordererId.equals(order.getOrdererId())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        deliveryId = order.getDeliveryId();
        responseSnapshot = OrderCreationSnapshot.from(order);
        status = OrderCreationStatus.COMPLETED;
    }

    /** 같은 키 재요청의 공통 판정. 키는 같은데 요청이 다르면 충돌이고, 논리 삭제된 기록은 복구 필요다. */
    public void validateReplayOf(OrderRequestIdentity identity) {
        if (!identity.key().equals(idempotencyKey) || !identity.requestHash().equals(requestHash)) {
            throw new BusinessException(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (isDeleted()) throw new BusinessException(OrderErrorCode.ORDER_CREATION_RECOVERY_REQUIRED);
    }

    /** 동기 실행의 재요청 판정. 처리 중이면 ORDER_CREATION_IN_PROGRESS다(비동기 접수는 validateReplayOf 후 상태로 분기한다). */
    public OrderCreationSnapshot replay(OrderRequestIdentity identity) {
        validateReplayOf(identity);
        return switch (status) {
            case PROCESSING -> throw new BusinessException(OrderErrorCode.ORDER_CREATION_IN_PROGRESS);
            case ABORTED -> throw new BusinessException(OrderErrorCode.ORDER_CREATION_ABORTED);
            case RECOVERY_REQUIRED -> throw new BusinessException(OrderErrorCode.ORDER_CREATION_RECOVERY_REQUIRED);
            case COMPLETED -> {
                if (responseSnapshot == null) throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
                yield responseSnapshot;
            }
        };
    }

    public void abort(UUID deliveryId, String failureCode, String failureMessage) {
        validateProcessing();
        this.deliveryId = deliveryId;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage == null || failureMessage.length() <= 255
                ? failureMessage : failureMessage.substring(0, 255);
        status = OrderCreationStatus.ABORTED;
    }

    public void requireRecovery(UUID deliveryId, CreationFailureReason reason) {
        validateProcessing();
        if (reason == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        this.deliveryId = deliveryId;
        failureReason = reason;
        status = OrderCreationStatus.RECOVERY_REQUIRED;
    }

    public void validateProcessing() {
        if (status != OrderCreationStatus.PROCESSING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /** O-09 원래 작업의 저장 권한을 회수한다. 기존 실패 사유는 별도 복구 이벤트가 보존한다. */
    public void claimRecovery(UUID requestId) {
        if (requestId == null || (recoveryId != null && !recoveryId.equals(requestId))
                || (status != OrderCreationStatus.PROCESSING && status != OrderCreationStatus.RECOVERY_REQUIRED)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        recoveryId = requestId;
        if (status == OrderCreationStatus.PROCESSING) {
            status = OrderCreationStatus.RECOVERY_REQUIRED;
            failureReason = CreationFailureReason.RECOVERY_TAKEOVER;
        }
    }

    /** O-09 CLOSED 확인과 주문 부재 재검증 후에만 복구를 종료한다. */
    public void finishRecovery(UUID requestId) {
        if (status != OrderCreationStatus.RECOVERY_REQUIRED || !requestId.equals(recoveryId)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        failureReason = null;
        failureCode = CommonErrorCode.SERVICE_UNAVAILABLE.code();
        failureMessage = "운영자가 배송 생성 종료를 확인하고 주문 생성 시도를 종료했습니다.";
        status = OrderCreationStatus.ABORTED;
    }
}
