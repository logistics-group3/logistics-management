-- SA 7.1 생성 Saga 보강. 주문 담당자의 구현 요청에 따른 order ADR 0007의 진행 기록이다.
CREATE TABLE p_order_creations
(
    order_creation_id UUID         PRIMARY KEY,
    status            VARCHAR(20)  NOT NULL,
    failure_reason    VARCHAR(30),
    order_id          UUID         NOT NULL,
    orderer_id        UUID         NOT NULL,
    delivery_id       UUID,
    created_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(100),

    CONSTRAINT uk_p_order_creations_order_id UNIQUE (order_id),
    CONSTRAINT ck_p_order_creations_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'ABORTED', 'RECOVERY_REQUIRED')),
    CONSTRAINT ck_p_order_creations_failure_reason CHECK (
        failure_reason IN ('DELIVERY_RESULT_UNKNOWN', 'ORDER_COMMIT_UNKNOWN', 'DELIVERY_DELETE_FAILED')),
    CONSTRAINT ck_p_order_creations_recovery_reason CHECK (
        (status = 'RECOVERY_REQUIRED' AND failure_reason IS NOT NULL)
        OR (status <> 'RECOVERY_REQUIRED' AND failure_reason IS NULL)),
    CONSTRAINT ck_p_order_creations_completed_delivery CHECK (status <> 'COMPLETED' OR delivery_id IS NOT NULL)
);

-- Note: 생성 전 기록은 주문 행 없이도 존재해야 한다. order_id를 포함해 물리 FK는 만들지 않는다.
CREATE INDEX idx_p_order_creations_orderer_id ON p_order_creations (orderer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_order_creations_delivery_id ON p_order_creations (delivery_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_order_creations_unresolved_created_at ON p_order_creations (created_at)
    WHERE deleted_at IS NULL AND status IN ('PROCESSING', 'RECOVERY_REQUIRED');
