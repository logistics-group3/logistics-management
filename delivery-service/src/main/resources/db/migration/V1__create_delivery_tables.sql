-- SA 4.6, 5.6 p_deliveries, p_delivery_routes. 구간 모델은 delivery ADR 0003(3구간).
-- 정합성 제약과 SA 5.6 인덱스만 둔다. 방어용 CHECK와 부분 인덱스는 기능 완성 후 실행계획을 보고 추가한다(결정 로그 13).
-- 상태·유형 컬럼은 팀 규칙대로 VARCHAR + CHECK로 값을 제한한다. 값을 바꾸면 새 마이그레이션에서 CHECK를 다시 만든다.
CREATE TABLE p_deliveries
(
    delivery_id         UUID         PRIMARY KEY,
    status              VARCHAR(30)  NOT NULL,
    pickup_address      VARCHAR(255) NOT NULL,
    delivery_address    VARCHAR(255) NOT NULL,
    recipient_name      VARCHAR(100) NOT NULL,
    recipient_slack_id  VARCHAR(50),
    order_id            UUID         NOT NULL,
    supplier_company_id UUID         NOT NULL,
    receiver_company_id UUID         NOT NULL,
    departure_hub_id    UUID         NOT NULL,
    destination_hub_id  UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(100),

    -- 주문 1건 = 배송 1건이고 I-09·I-13 멱등키다. 논리 삭제 뒤에도 같은 주문으로 다시 만들지 않으므로 일반 유니크 제약이다.
    CONSTRAINT uk_p_deliveries_order_id UNIQUE (order_id),
    CONSTRAINT ck_p_deliveries_status CHECK (status IN ('PICKUP_WAITING', 'PICKING_UP', 'HUB_WAITING', 'HUB_MOVING',
                                                        'DEST_HUB_ARRIVED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELED'))
);

CREATE INDEX idx_p_deliveries_status ON p_deliveries (status);
CREATE INDEX idx_p_deliveries_supplier_company_id ON p_deliveries (supplier_company_id);
CREATE INDEX idx_p_deliveries_receiver_company_id ON p_deliveries (receiver_company_id);
CREATE INDEX idx_p_deliveries_departure_hub_id ON p_deliveries (departure_hub_id);
CREATE INDEX idx_p_deliveries_destination_hub_id ON p_deliveries (destination_hub_id);

CREATE TABLE p_delivery_routes
(
    delivery_route_id         UUID         PRIMARY KEY,
    sequence                  INT          NOT NULL,
    route_type                VARCHAR(20)  NOT NULL,
    expected_distance_km      DECIMAL(8, 2),
    expected_duration_minutes INT,
    actual_distance_km        DECIMAL(8, 2),
    actual_duration_minutes   INT,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    delivery_id               UUID         NOT NULL,
    departure_hub_id          UUID,
    arrival_hub_id            UUID,
    manager_id                UUID         NOT NULL,
    created_at                TIMESTAMP    NOT NULL,
    created_by                VARCHAR(100) NOT NULL,
    updated_at                TIMESTAMP    NOT NULL,
    updated_by                VARCHAR(100) NOT NULL,
    deleted_at                TIMESTAMP,
    deleted_by                VARCHAR(100),

    -- 같은 서비스 안의 참조라 FK를 건다(서비스 간 참조는 FK 없이 논리 참조).
    CONSTRAINT fk_p_delivery_routes_delivery FOREIGN KEY (delivery_id) REFERENCES p_deliveries (delivery_id),
    CONSTRAINT uk_p_delivery_routes_delivery_id_sequence UNIQUE (delivery_id, sequence),
    CONSTRAINT ck_p_delivery_routes_route_type CHECK (route_type IN ('PICKUP', 'HUB_TRANSFER', 'LAST_MILE')),
    CONSTRAINT ck_p_delivery_routes_status CHECK (status IN ('WAITING', 'MOVING', 'ARRIVED', 'CANCELED'))
);

CREATE INDEX idx_p_delivery_routes_manager_id ON p_delivery_routes (manager_id);
CREATE INDEX idx_p_delivery_routes_status ON p_delivery_routes (status);

-- 테이블·컬럼 설명(SA 5.6). DB에 저장되어 DB 도구와 ERD에서 보인다. Audit 컬럼 6개는 공통이라 생략한다.
COMMENT ON TABLE p_deliveries IS '배송. 주문 1건 = 배송 1건, 구간(p_delivery_routes)을 함께 관리하는 애그리거트 루트';
COMMENT ON COLUMN p_deliveries.delivery_id IS '배송 식별자';
COMMENT ON COLUMN p_deliveries.status IS '배송 상태. 구간 상태가 바뀔 때 따라 바뀐다(SA 2.5)';
COMMENT ON COLUMN p_deliveries.pickup_address IS '집하지 주소(공급업체 주소)';
COMMENT ON COLUMN p_deliveries.delivery_address IS '배송지 주소(요청업체 주소)';
COMMENT ON COLUMN p_deliveries.recipient_name IS '수령인. 기본값은 요청업체명';
COMMENT ON COLUMN p_deliveries.recipient_slack_id IS '수령인 Slack ID. 배송 생성 시 user I-01로 조회, 없으면 NULL';
COMMENT ON COLUMN p_deliveries.order_id IS '주문 ID(order-service 논리 참조). I-09·I-13 멱등키';
COMMENT ON COLUMN p_deliveries.supplier_company_id IS '공급업체 ID(company-service 논리 참조). 물건을 내보내는 업체';
COMMENT ON COLUMN p_deliveries.receiver_company_id IS '요청업체 ID(company-service 논리 참조). 물건을 받는 업체';
COMMENT ON COLUMN p_deliveries.departure_hub_id IS '출발 허브 = 공급업체 관리 허브(hub-service 논리 참조). 배송 생성 시점 값';
COMMENT ON COLUMN p_deliveries.destination_hub_id IS '목적지 허브 = 요청업체 관리 허브(hub-service 논리 참조). 배송 생성 시점 값';

COMMENT ON TABLE p_delivery_routes IS '배송 구간. 배송 1건당 2~3건(집하, 허브 이동, 업체 배송). 같은 허브 소속이면 허브 이동 없음';
COMMENT ON COLUMN p_delivery_routes.delivery_route_id IS '구간 식별자';
COMMENT ON COLUMN p_delivery_routes.sequence IS '구간 순번(1부터). 앞 구간이 ARRIVED여야 다음 구간이 MOVING이 된다';
COMMENT ON COLUMN p_delivery_routes.route_type IS 'PICKUP(공급업체 → 공급업체 허브), HUB_TRANSFER(허브 간), LAST_MILE(요청업체 허브 → 요청업체)';
COMMENT ON COLUMN p_delivery_routes.expected_distance_km IS '예상 거리(km). HUB_TRANSFER만 hub p_hub_routes 값으로 채운다';
COMMENT ON COLUMN p_delivery_routes.expected_duration_minutes IS '예상 소요시간(분). HUB_TRANSFER만 채운다';
COMMENT ON COLUMN p_delivery_routes.actual_distance_km IS '실제 거리(km). 도착 처리 시 입력';
COMMENT ON COLUMN p_delivery_routes.actual_duration_minutes IS '실제 소요시간(분). 도착 처리 시 입력';
COMMENT ON COLUMN p_delivery_routes.status IS '구간 상태. WAITING → MOVING → ARRIVED, 주문 취소 시 CANCELED';
COMMENT ON COLUMN p_delivery_routes.delivery_id IS '배송 ID(p_deliveries FK)';
COMMENT ON COLUMN p_delivery_routes.departure_hub_id IS '출발 허브(hub-service 논리 참조). PICKUP은 업체에서 출발하므로 NULL';
COMMENT ON COLUMN p_delivery_routes.arrival_hub_id IS '도착 허브(hub-service 논리 참조). LAST_MILE은 업체로 도착하므로 NULL';
COMMENT ON COLUMN p_delivery_routes.manager_id IS '구간 담당 배송담당자(user-service 논리 참조). 생성 시 자동 배정, MASTER 재배정 가능';
