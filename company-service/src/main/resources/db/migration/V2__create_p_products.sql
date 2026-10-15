-- SA 4.4, 5.1, 5.4 p_products. 상품은 업체의 하위 리소스이고 허브는 따로 저장하지 않는다(업체의 hub_id를 따른다).
CREATE TABLE p_products
(
    product_id  UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    -- 원 단위 정수(company 결정 0003)
    price       BIGINT       NOT NULL,
    company_id  UUID         NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    created_by  VARCHAR(100) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    updated_by  VARCHAR(100) NOT NULL,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(100),

    -- 같은 서비스 안의 참조라 실제 FK를 둔다.
    CONSTRAINT fk_p_products_company FOREIGN KEY (company_id) REFERENCES p_companies (company_id),
    CONSTRAINT ck_p_products_price CHECK (price >= 0)
);

-- 업체별 상품 조회(P-02 companyId 필터, C-05 소속 상품 일괄 삭제)에 쓴다.
CREATE INDEX idx_p_products_company_id ON p_products (company_id) WHERE deleted_at IS NULL;
