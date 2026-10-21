-- SA 4.3, 5.3 p_hubs, p_hub_routes. 테이블 이름에 스키마를 붙이지 않는다(실행 스키마는 spring.flyway.schemas가 정한다).
CREATE TABLE p_hubs
(
    hub_id     UUID           PRIMARY KEY,
    name       VARCHAR(50)    NOT NULL,
    address    VARCHAR(255)   NOT NULL,
    latitude   DECIMAL(10, 7) NOT NULL,
    longitude  DECIMAL(10, 7) NOT NULL,
    created_at TIMESTAMP      NOT NULL,
    created_by VARCHAR(100)   NOT NULL,
    updated_at TIMESTAMP      NOT NULL,
    updated_by VARCHAR(100)   NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100),

    CONSTRAINT uk_p_hubs_name UNIQUE (name)
);

CREATE TABLE p_hub_routes
(
    hub_route_id     UUID          PRIMARY KEY,
    departure_hub_id UUID          NOT NULL,
    arrival_hub_id   UUID          NOT NULL,
    duration_minutes INT           NOT NULL,
    distance_km      DECIMAL(8, 2) NOT NULL,
    created_at       TIMESTAMP     NOT NULL,
    created_by       VARCHAR(100)  NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    updated_by       VARCHAR(100)  NOT NULL,
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(100),

    -- 같은 서비스 테이블이므로 실제 FK를 둔다(서비스 간 참조와 달리)
    CONSTRAINT fk_p_hub_routes_departure_hub FOREIGN KEY (departure_hub_id) REFERENCES p_hubs (hub_id),
    CONSTRAINT fk_p_hub_routes_arrival_hub FOREIGN KEY (arrival_hub_id) REFERENCES p_hubs (hub_id),
    CONSTRAINT ck_p_hub_routes_duration_minutes CHECK (duration_minutes > 0),
    CONSTRAINT ck_p_hub_routes_distance_km CHECK (distance_km > 0),
    CONSTRAINT ck_p_hub_routes_different_hubs CHECK (departure_hub_id <> arrival_hub_id)
);

-- 방향별 경로는 활성 행 기준으로 하나만 존재한다(SA 5.3). 논리 삭제 후 같은 경로를 다시 둘 수 있도록 부분 유니크 인덱스로 둔다.
-- 출발 허브 기준 조회(H-03, I-03)는 이 인덱스의 선두 컬럼으로 처리된다.
CREATE UNIQUE INDEX uk_p_hub_routes_departure_arrival
    ON p_hub_routes (departure_hub_id, arrival_hub_id) WHERE deleted_at IS NULL;
