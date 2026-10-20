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

@Getter
@Entity
@Table(name = "p_order_creation_recoveries")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreationRecovery extends BaseEntity {
    public enum Status { RUNNING, DEFERRED, COMPLETED }

    @Id @Column(name = "recovery_id") private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "execution_id", nullable = false) private UUID executionId;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(name = "expected_status", nullable = false, length = 20)
    private OrderCreationStatus expectedStatus;
    @Column(nullable = false, length = 255) private String message;
    @Column(name = "attempt_id", nullable = false, unique = true) private UUID attemptId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "requested_by", nullable = false) private UUID requestedBy;

    public static OrderCreationRecovery start(UUID id, UUID attemptId, UUID orderId, UUID requestedBy,
            OrderCreationStatus expectedStatus, String reason) {
        OrderCreationRecovery recovery = new OrderCreationRecovery();
        recovery.id = id;
        recovery.attemptId = attemptId;
        recovery.orderId = orderId;
        recovery.requestedBy = requestedBy;
        recovery.expectedStatus = expectedStatus;
        recovery.reason = reason;
        recovery.status = Status.RUNNING;
        recovery.executionId = UUID.randomUUID();
        recovery.message = "복구 실행권을 확보했습니다.";
        return recovery;
    }

    public void validateRequest(UUID attemptId, UUID actor, OrderCreationStatus expected, String reason) {
        if (!this.attemptId.equals(attemptId) || !requestedBy.equals(actor)
                || expectedStatus != expected || !this.reason.equals(reason)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION, "같은 복구 ID에 다른 요청을 사용할 수 없습니다.");
        }
    }

    /** 실행 중 중복은 관찰만 한다. 명시적 재개는 이전 실행 ID가 일치할 때만 실행권을 바꾼다. */
    public boolean resume(UUID previousExecutionId) {
        if (status == Status.COMPLETED) return false;
        if (status == Status.RUNNING && previousExecutionId == null) return false;
        if (previousExecutionId != null && !executionId.equals(previousExecutionId)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION, "복구 실행 ID가 변경됐습니다.");
        }
        executionId = UUID.randomUUID();
        status = Status.RUNNING;
        message = "복구를 재개했습니다.";
        return true;
    }

    public void validateExecution(UUID executionId) {
        if (status != Status.RUNNING || !this.executionId.equals(executionId)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION, "현재 복구 작업의 실행권이 아닙니다.");
        }
    }

    public void defer(UUID executionId, String message) {
        validateExecution(executionId);
        status = Status.DEFERRED;
        this.message = message;
    }

    public void complete(UUID executionId) {
        validateExecution(executionId);
        status = Status.COMPLETED;
        message = "배송 생성 종료와 주문 부재를 확인해 ABORTED로 종료했습니다.";
    }
}
