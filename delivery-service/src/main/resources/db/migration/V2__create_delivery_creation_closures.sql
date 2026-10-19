-- 이슈 #14. 종료된 주문 생성 시도의 지연된 I-09가 활성 배송을 만들지 못하게 하는 종료 표시.
-- I-13(배송 삭제·생성 보상)이 대상 배송이 없어도 이 표를 남기고, I-09는 표가 있으면 만들지 않는다.
-- order는 시도마다 order_id를 새로 만들므로 order_id가 곧 시도 식별자다. 갱신이 없는 기록형 데이터라 updated·deleted 컬럼은 두지 않는다.
CREATE TABLE p_delivery_creation_closures
(
    order_id  UUID         PRIMARY KEY,
    closed_at TIMESTAMP    NOT NULL,
    closed_by VARCHAR(100) NOT NULL
);

COMMENT ON TABLE p_delivery_creation_closures IS '종료된 배송 생성 시도. I-13이 남기고, 있으면 같은 order_id의 I-09는 배송을 만들지 않는다(이슈 #14)';
COMMENT ON COLUMN p_delivery_creation_closures.order_id IS '주문 ID이자 생성 시도 식별자. order-service p_orders.order_id 논리 참조';
COMMENT ON COLUMN p_delivery_creation_closures.closed_at IS '종료 표시를 남긴 시각';
COMMENT ON COLUMN p_delivery_creation_closures.closed_by IS '종료 요청자. 내부 호출은 X-Username, 없으면 SYSTEM';

-- 이 변경 이전에 논리 삭제된 배송은 종료 표시가 없다. 같은 order_id의 I-09가 order_id 유니크 위반(500)이 아니라
-- DELIVERY_CREATION_CLOSED(409)로 거절되도록 삭제 기록에서 채운다.
INSERT INTO p_delivery_creation_closures (order_id, closed_at, closed_by)
SELECT order_id, deleted_at, COALESCE(deleted_by, 'SYSTEM')
FROM p_deliveries
WHERE deleted_at IS NOT NULL;
