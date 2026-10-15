-- SA 5.2 확장: ADR 0016. 기존 회원은 이메일 미설정으로 유지한다.
ALTER TABLE p_users ADD COLUMN slack_email VARCHAR(254);
ALTER TABLE p_users ADD CONSTRAINT ck_p_users_slack_email_normalized
    CHECK (slack_email IS NULL OR (slack_email <> '' AND slack_email = BTRIM(slack_email)));
COMMENT ON COLUMN p_users.slack_email IS '배송담당자 Slack DM용 워크스페이스 계정 이메일, 미설정이면 발송 생략';
