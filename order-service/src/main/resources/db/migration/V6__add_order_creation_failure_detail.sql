-- 이슈 #16. 주문 생성이 비동기 접수로 바뀌어 실패 사유를 요청 응답으로 돌려줄 수 없다. 진행 조회가 사유를 보여 주도록 기록한다.
ALTER TABLE p_order_creations
    ADD COLUMN failure_code    VARCHAR(50),
    ADD COLUMN failure_message VARCHAR(255),
    ADD CONSTRAINT ck_p_order_creations_failure_detail CHECK (
        status IN ('ABORTED', 'RECOVERY_REQUIRED') OR (failure_code IS NULL AND failure_message IS NULL));

COMMENT ON COLUMN p_order_creations.failure_code IS '실패로 종료(ABORTED)된 원인 코드. 하위 서비스 4xx의 에러 코드이거나 SERVICE_UNAVAILABLE·INTERNAL_SERVER_ERROR';
COMMENT ON COLUMN p_order_creations.failure_message IS '실패 원인 안내 문구. 진행 조회에 그대로 보여 준다(최대 255자)';
