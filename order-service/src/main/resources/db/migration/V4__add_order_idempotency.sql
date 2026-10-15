-- SA O-01, order ADR 0008. V3 이전·키 없는 생성 기록은 NULL을 유지한다.
ALTER TABLE p_order_creations
    ADD COLUMN idempotency_key VARCHAR(128) COLLATE "C",
    ADD COLUMN request_hash VARCHAR(67),
    ADD COLUMN response_snapshot JSONB,
    ADD CONSTRAINT uk_p_order_creations_user_key UNIQUE (orderer_id, idempotency_key),
    ADD CONSTRAINT ck_p_order_creations_request_identity CHECK (
        (idempotency_key IS NULL AND request_hash IS NULL)
        OR (idempotency_key IS NOT NULL AND request_hash IS NOT NULL
            AND idempotency_key ~ '^[A-Za-z0-9._:-]{1,128}$'
            AND request_hash ~ '^v1:[0-9a-f]{64}$')),
    ADD CONSTRAINT ck_p_order_creations_response_snapshot CHECK (
        response_snapshot IS NULL OR (status = 'COMPLETED' AND jsonb_typeof(response_snapshot) = 'object')),
    ADD CONSTRAINT ck_p_order_creations_keyed_completion CHECK (
        idempotency_key IS NULL OR status <> 'COMPLETED' OR response_snapshot IS NOT NULL);

-- Note: UNIQUE는 삭제 이력도 포함한다. TTL·논리 삭제를 키 재사용 권한으로 취급하지 않는다.
