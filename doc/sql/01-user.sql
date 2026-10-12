-- =====================================================================
-- 01-user.sql — user_db 테이블 (SA 5.2)
-- user-db 컨테이너가 처음 뜰 때(볼륨이 비어 있을 때) 실행된다. DB·계정은 compose 환경 변수가 만든다.
-- 변경 후 반영: script/run.sh --reset (docker compose down -v)
-- =====================================================================

CREATE TABLE p_users (
    user_id             UUID          NOT NULL,
    username            VARCHAR(10)   NOT NULL,
    password            VARCHAR(255)  NOT NULL,
    name                VARCHAR(50)   NOT NULL,
    slack_id            VARCHAR(50)   NOT NULL,
    affiliation_type    VARCHAR(20)   NOT NULL,
    affiliation_name    VARCHAR(100)  NOT NULL,
    role                VARCHAR(20)   NOT NULL DEFAULT 'USER',
    is_delivery_manager BOOLEAN       NOT NULL DEFAULT FALSE,
    -- 공통 Audit 컬럼 (BaseEntity)
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          VARCHAR(100)  NOT NULL,
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_by          VARCHAR(100)  NOT NULL,
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(100),

    CONSTRAINT pk_users PRIMARY KEY (user_id),
    -- 탈퇴 후에도 재사용 불가 → 부분 인덱스가 아닌 전체 UNIQUE
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_affiliation_type CHECK (affiliation_type IN ('COMPANY', 'HUB')),
    CONSTRAINT ck_users_role CHECK (role IN ('MASTER', 'USER'))
);

COMMENT ON TABLE p_users IS '사용자 (회원가입·로그인, 권한 MASTER/USER, 배송담당자 여부)';
COMMENT ON COLUMN p_users.username IS '로그인 ID. 소문자·숫자 4~10자. 탈퇴 후에도 재사용 불가';
COMMENT ON COLUMN p_users.password IS 'BCrypt 해시';
COMMENT ON COLUMN p_users.affiliation_name IS '소속 업체명 또는 허브명';
COMMENT ON COLUMN p_users.is_delivery_manager IS '배송담당자 여부 (MASTER가 부여)';

CREATE INDEX idx_users_role ON p_users (role) WHERE deleted_at IS NULL;
