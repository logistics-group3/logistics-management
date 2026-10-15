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
