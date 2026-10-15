-- SA 4.4, 5.1, 5.4 p_companies. 테이블 이름에 스키마를 붙이지 않는다(실행 스키마는 spring.flyway.schemas가 정한다).
CREATE TABLE p_companies
(
    company_id UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    hub_id     UUID         NOT NULL,
    manager_id UUID         NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);

-- hub_id, manager_id는 다른 서비스를 가리키는 논리 참조라 FK 없이 인덱스만 둔다(SA 5.1).
-- 검색(C-02)은 활성 업체만 보므로 부분 인덱스로 둔다.
CREATE INDEX idx_p_companies_hub_id ON p_companies (hub_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_p_companies_manager_id ON p_companies (manager_id) WHERE deleted_at IS NULL;
