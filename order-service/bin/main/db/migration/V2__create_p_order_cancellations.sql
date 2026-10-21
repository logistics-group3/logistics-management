-- SA 7.2 취소 Saga 보강. 주문 담당자가 승인한 order ADR 0006의 내부 진행 기록이다.
CREATE TABLE p_order_cancellations
(
    order_cancellation_id UUID         PRIMARY KEY,
    status                VARCHAR(20)  NOT NULL,
    failure_reason        VARCHAR(30),
    order_id              UUID         NOT NULL,
    delivery_id           UUID         NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    updated_by            VARCHAR(100) NOT NULL,
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(100),

    CONSTRAINT fk_p_order_cancellations_order FOREIGN KEY (order_id) REFERENCES p_orders (order_id),
    CONSTRAINT ck_p_order_cancellations_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'ABORTED', 'RECOVERY_REQUIRED')),
    CONSTRAINT ck_p_order_cancellations_failure_reason CHECK (
        failure_reason IN ('DELIVERY_RESULT_UNKNOWN', 'ORDER_COMMIT_UNKNOWN', 'REVERT_FAILED')),
    CONSTRAINT ck_p_order_cancellations_recovery_reason CHECK (
        (status = 'RECOVERY_REQUIRED' AND failure_reason IS NOT NULL)
        OR (status <> 'RECOVERY_REQUIRED' AND failure_reason IS NULL))
);

-- Note: 재시작·여러 인스턴스에서도 미해결 시도가 있으면 새 취소를 시작하지 못한다. 자동 만료하지 않는다.
CREATE UNIQUE INDEX uk_p_order_cancellations_unresolved_order ON p_order_cancellations (order_id)
    WHERE deleted_at IS NULL AND status IN ('PROCESSING', 'RECOVERY_REQUIRED');
CREATE INDEX idx_p_order_cancellations_order_id ON p_order_cancellations (order_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_order_cancellations_delivery_id ON p_order_cancellations (delivery_id) WHERE deleted_at IS NULL;
