-- SA 4.2, 5.1, 5.2 p_users. 테이블 이름에 스키마를 붙이지 않는다(실행 스키마는 spring.flyway.schemas가 정한다).
CREATE TABLE p_users
(
    user_id             UUID         PRIMARY KEY,
    username            VARCHAR(10)  NOT NULL,
    password            VARCHAR(255) NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    slack_id            VARCHAR(50)  NOT NULL,
    affiliation_type    VARCHAR(20)  NOT NULL,
    affiliation_name    VARCHAR(100) NOT NULL,
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_delivery_manager BOOLEAN      NOT NULL DEFAULT FALSE,
    hub_id              UUID,
    created_at          TIMESTAMP    NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(100),

    -- 탈퇴 후에도 username 재사용 불가(SA 5.2)라 부분 인덱스가 아닌 일반 유니크 제약
    CONSTRAINT uk_p_users_username UNIQUE (username),
    CONSTRAINT ck_p_users_role CHECK (role IN ('MASTER', 'USER')),
    CONSTRAINT ck_p_users_affiliation_type CHECK (affiliation_type IN ('COMPANY', 'HUB')),
    -- 배송담당자면 소속 허브가 필수(SA 5.2)
    CONSTRAINT ck_p_users_delivery_manager_hub CHECK (is_delivery_manager = FALSE OR hub_id IS NOT NULL)
);

-- 논리 참조 컬럼 인덱스(SA 5.1). 허브별 배송담당자 조회(I-14)에 쓴다. 탈퇴자는 조회 대상이 아니라 부분 인덱스로 둔다.
CREATE INDEX idx_p_users_hub_id ON p_users (hub_id) WHERE deleted_at IS NULL;
