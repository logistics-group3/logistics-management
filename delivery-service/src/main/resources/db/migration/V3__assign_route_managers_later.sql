-- 결정 로그 27. 구간 담당자 배정을 배송 생성(I-09)에서 떼어 내 허브별 비동기 배치 배정으로 옮긴다.
-- 배송은 담당자 없이(manager_id NULL, status WAITING) 저장되고, 배정 작업이 허브마다 대기 구간을 모아 배정한다.

ALTER TABLE p_delivery_routes ALTER COLUMN manager_id DROP NOT NULL;

-- 구간 담당 허브. 지금까지는 route_type과 출발·도착 허브로 계산했다(PICKUP은 도착 허브, 나머지는 출발 허브).
-- 배정 작업이 "이 허브의 대기 구간"을 인덱스로 찾도록 컬럼으로 저장한다.
ALTER TABLE p_delivery_routes ADD COLUMN managing_hub_id UUID;
UPDATE p_delivery_routes
SET managing_hub_id = CASE WHEN route_type = 'PICKUP' THEN arrival_hub_id ELSE departure_hub_id END;
ALTER TABLE p_delivery_routes ALTER COLUMN managing_hub_id SET NOT NULL;

-- 담당자가 처음 정해진 시각. 배정 지연(생성 → 배정)을 잰다. 기존 구간은 생성 때 배정됐으므로 생성 시각으로 채운다.
ALTER TABLE p_delivery_routes ADD COLUMN assigned_at TIMESTAMP;
UPDATE p_delivery_routes SET assigned_at = created_at WHERE manager_id IS NOT NULL;

-- 배정 대기 구간만 담는 부분 인덱스. 배정이 끝난 구간은 빠지므로 대기가 없으면 거의 비어 있다.
CREATE INDEX idx_p_delivery_routes_pending_assignment ON p_delivery_routes (managing_hub_id)
    WHERE manager_id IS NULL AND status = 'WAITING' AND deleted_at IS NULL;

COMMENT ON COLUMN p_delivery_routes.manager_id IS '구간 담당 배송담당자(user-service 논리 참조). 배송 생성 뒤 허브별 배정 작업이 채운다. NULL이면 배정 대기. MASTER 재배정 가능';
COMMENT ON COLUMN p_delivery_routes.managing_hub_id IS '구간 담당 허브(hub-service 논리 참조). PICKUP은 도착 허브, HUB_TRANSFER·LAST_MILE은 출발 허브. 배정 후보는 이 허브 소속';
COMMENT ON COLUMN p_delivery_routes.assigned_at IS '담당자가 처음 정해진 시각. NULL이면 배정 대기';
