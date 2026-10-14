-- Hibernate DDL 이후 실행된다 (spring.jpa.defer-datasource-initialization=true).
-- 논리 삭제된 사용자의 username은 재사용할 수 있도록 부분 유니크 인덱스로 건다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_p_users_username ON p_users (username) WHERE deleted_at IS NULL;
