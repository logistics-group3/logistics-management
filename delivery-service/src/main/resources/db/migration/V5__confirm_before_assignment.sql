-- SA 4.6, 5.6 p_deliveries
-- 결정 로그 29. 배정을 "배송 저장 뒤"에서 "주문 확정 뒤"로 옮긴다.
-- I-09는 배송을 미확정으로 저장하고, order가 주문을 커밋한 뒤 I-19로 확정하면 그때 배정한다.
-- 후보가 없는 상태가 기준 시간을 넘으면 배송을 수동 지정 필요로 바꾸고 자동 배정은 그 배송을 건너뛴다.

ALTER TABLE p_deliveries ADD COLUMN confirmed_at TIMESTAMP;
-- 기존 배송은 커밋된 주문의 배송이다(지금까지는 주문 커밋 전에도 배정했다). 확정된 것으로 본다.
UPDATE p_deliveries SET confirmed_at = created_at;

ALTER TABLE p_deliveries ADD COLUMN manual_assignment_requested_at TIMESTAMP;

-- 확정을 받지 못한 배송을 찾는 안전망용. 평소에는 확정까지 수 ms라 거의 비어 있다.
CREATE INDEX idx_p_deliveries_unconfirmed ON p_deliveries (created_at)
    WHERE confirmed_at IS NULL AND deleted_at IS NULL;

COMMENT ON COLUMN p_deliveries.confirmed_at IS '주문 확정 시각(I-19). NULL이면 주문이 아직 확정되지 않아 구간 담당자를 배정하지 않는다';
COMMENT ON COLUMN p_deliveries.manual_assignment_requested_at IS '수동 지정 요청 시각. 후보가 없는 상태가 기준 시간을 넘으면 기록하고 MASTER에게 알린다. 값이 있으면 자동 배정이 건너뛴다';
