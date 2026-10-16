-- SA 4.2, 5.2, U-01 / ADR 0016: 신규 회원가입은 Slack 이메일만 받는다.
-- 기존 Slack ID를 보존하며 이메일을 ID로 저장하거나 가짜 ID를 만들지 않는다.
ALTER TABLE p_users ALTER COLUMN slack_id DROP NOT NULL;
COMMENT ON COLUMN p_users.slack_id IS '기존 Slack 멤버 ID. 이메일로 가입한 신규 사용자는 NULL';
