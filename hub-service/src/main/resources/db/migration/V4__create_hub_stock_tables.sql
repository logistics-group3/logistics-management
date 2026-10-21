-- SA 4.3, 5.3 p_hub_stocks, p_hub_stock_histories (재고 관리 고도화). 테이블 이름에 스키마를 붙이지 않는다.
CREATE TABLE p_hub_stocks
(
    hub_stock_id UUID         PRIMARY KEY,
    quantity     INT          NOT NULL DEFAULT 0,
    hub_id       UUID         NOT NULL,
    product_id   UUID         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    deleted_at   TIMESTAMP,
    deleted_by   VARCHAR(100),

    -- 허브는 같은 서비스 테이블이라 FK를 둔다. product_id는 company-service 상품이므로 FK를 두지 않는다.
    CONSTRAINT fk_p_hub_stocks_hub FOREIGN KEY (hub_id) REFERENCES p_hubs (hub_id),
    CONSTRAINT ck_p_hub_stocks_quantity CHECK (quantity >= 0)
);

-- 허브 × 상품당 활성 행은 하나다(I-04 멱등키, H-06 레코드 생성 경합 방지).
CREATE UNIQUE INDEX uk_p_hub_stocks_hub_product
    ON p_hub_stocks (hub_id, product_id) WHERE deleted_at IS NULL;

-- 기록형 데이터라 update·delete가 없어 Audit은 created_at, created_by만 둔다.
CREATE TABLE p_hub_stock_histories
(
    hub_stock_history_id UUID         PRIMARY KEY,
    hub_stock_id         UUID         NOT NULL,
    order_id             UUID,
    change_type          VARCHAR(20)  NOT NULL,
    quantity             INT          NOT NULL,
    quantity_after       INT          NOT NULL,
    created_at           TIMESTAMP    NOT NULL,
    created_by           VARCHAR(100) NOT NULL,

    CONSTRAINT fk_p_hub_stock_histories_hub_stock FOREIGN KEY (hub_stock_id) REFERENCES p_hub_stocks (hub_stock_id),
    CONSTRAINT ck_p_hub_stock_histories_change_type CHECK (change_type IN ('INBOUND', 'DECREASE', 'RESTORE')),
    CONSTRAINT ck_p_hub_stock_histories_quantity CHECK (quantity > 0),
    CONSTRAINT ck_p_hub_stock_histories_quantity_after CHECK (quantity_after >= 0)
);

-- 같은 주문이 같은 허브 재고를 두 번 차감·복원하지 않게 막는 멱등키(SA 5.3, 7.3). 입고는 order_id가 NULL이라 제외한다.
CREATE UNIQUE INDEX uk_p_hub_stock_histories_stock_order_type
    ON p_hub_stock_histories (hub_stock_id, order_id, change_type) WHERE order_id IS NOT NULL;

-- 재고 복원(I-06)이 주문 ID로 차감 이력을 찾을 때 쓴다.
CREATE INDEX idx_p_hub_stock_histories_order_id
    ON p_hub_stock_histories (order_id) WHERE order_id IS NOT NULL;
