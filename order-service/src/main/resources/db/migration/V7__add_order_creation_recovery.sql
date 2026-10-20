-- SA 5.5·7.1, ADR 0019 운영자 복구. 기존 생성 기록과 복구 이력을 같은 DB에서 보호한다.
ALTER TABLE p_order_creations ADD COLUMN recovery_id UUID;
ALTER TABLE p_order_creations DROP CONSTRAINT ck_p_order_creations_failure_reason;
ALTER TABLE p_order_creations ADD CONSTRAINT ck_p_order_creations_failure_reason CHECK (
    failure_reason IN ('DELIVERY_RESULT_UNKNOWN', 'ORDER_COMMIT_UNKNOWN', 'DELIVERY_DELETE_FAILED', 'RECOVERY_TAKEOVER'));

CREATE TABLE p_order_creation_recoveries (
    recovery_id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'DEFERRED', 'COMPLETED')),
    execution_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    expected_status VARCHAR(20) NOT NULL CHECK (expected_status IN ('PROCESSING', 'RECOVERY_REQUIRED')),
    message VARCHAR(255) NOT NULL,
    attempt_id UUID NOT NULL UNIQUE REFERENCES p_order_creations(order_creation_id),
    order_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);
CREATE TABLE p_order_creation_recovery_events (
    event_id UUID PRIMARY KEY,
    step VARCHAR(30) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    execution_id UUID NOT NULL,
    recovery_id UUID NOT NULL REFERENCES p_order_creation_recoveries(recovery_id),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);
CREATE INDEX idx_p_order_creation_recoveries_order ON p_order_creation_recoveries(order_id);
CREATE INDEX idx_p_order_creation_recovery_events_recovery ON p_order_creation_recovery_events(recovery_id, created_at);
COMMENT ON TABLE p_order_creation_recoveries IS '주문 생성의 운영자 복구 작업과 명시적 실행권';
COMMENT ON TABLE p_order_creation_recovery_events IS '실행 ID별 복구 단계와 관찰의 추가 전용 이력';
COMMENT ON COLUMN p_order_creations.recovery_id IS '원래 작업의 저장 권한을 회수한 복구 요청 ID';
