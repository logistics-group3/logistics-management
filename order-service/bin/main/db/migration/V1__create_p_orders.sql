-- SA 4.5, 5.1, 5.5 p_orders. 실행 스키마는 spring.flyway.schemas가 정한다.
CREATE TABLE p_orders
(
    order_id            UUID         PRIMARY KEY,
    product_name        VARCHAR(100) NOT NULL,
    unit_price          BIGINT       NOT NULL,
    quantity            INT          NOT NULL,
    total_price         BIGINT       NOT NULL,
    request_note        VARCHAR(500),
    status              VARCHAR(20)  NOT NULL,
    orderer_id          UUID         NOT NULL,
    supplier_company_id UUID         NOT NULL,
    receiver_company_id UUID         NOT NULL,
    product_id          UUID         NOT NULL,
    delivery_id         UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(100),

    CONSTRAINT ck_p_orders_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_p_orders_quantity CHECK (quantity > 0),
    CONSTRAINT ck_p_orders_total_price CHECK (total_price >= 0),
    CONSTRAINT ck_p_orders_status CHECK (status IN ('ORDERED', 'CANCELED')),
    CONSTRAINT ck_p_orders_different_companies CHECK (supplier_company_id <> receiver_company_id)
);

-- 서비스 간 참조는 UUID로만 보관한다. SA 5.1에 따라 활성 주문에만 배송 유니크 제약을 적용한다.
CREATE UNIQUE INDEX uk_p_orders_delivery_id ON p_orders (delivery_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_orders_orderer_id ON p_orders (orderer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_orders_supplier_company_id ON p_orders (supplier_company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_orders_receiver_company_id ON p_orders (receiver_company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_orders_product_id ON p_orders (product_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_orders_created_at ON p_orders (created_at) WHERE deleted_at IS NULL;
