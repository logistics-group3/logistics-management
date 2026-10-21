-- SA 5.5, 6.7 O-06, 7.5. 주문 담당자 승인에 따른 order ADR 0009.
CREATE TABLE p_order_deletions
(
    order_deletion_id UUID         PRIMARY KEY,
    status            VARCHAR(20)  NOT NULL,
    eligibility       VARCHAR(20)  NOT NULL,
    failure_reason    VARCHAR(30),
    order_id          UUID         NOT NULL,
    delivery_id       UUID         NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(100),

    CONSTRAINT uk_p_order_deletions_order_id UNIQUE (order_id),
    CONSTRAINT fk_p_order_deletions_order FOREIGN KEY (order_id) REFERENCES p_orders (order_id),
    CONSTRAINT ck_p_order_deletions_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'RECOVERY_REQUIRED')),
    CONSTRAINT ck_p_order_deletions_eligibility CHECK (eligibility IN ('ORDER_CANCELED', 'DELIVERY_DELIVERED')),
    CONSTRAINT ck_p_order_deletions_failure_reason CHECK (
        failure_reason IN ('DELIVERY_DELETE_FAILED', 'ORDER_WRITE_FAILED', 'ORDER_COMMIT_UNKNOWN')),
    CONSTRAINT ck_p_order_deletions_recovery_reason CHECK (
        (status = 'RECOVERY_REQUIRED' AND failure_reason IS NOT NULL)
        OR (status <> 'RECOVERY_REQUIRED' AND failure_reason IS NULL))
);

CREATE INDEX idx_p_order_deletions_delivery_id ON p_order_deletions (delivery_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_order_deletions_unresolved_created_at ON p_order_deletions (created_at)
    WHERE deleted_at IS NULL AND status IN ('PROCESSING', 'RECOVERY_REQUIRED');
