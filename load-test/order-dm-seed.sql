-- 주문 → DM 끝단 측정용. delivery-e2e.sql을 적용한 뒤에 넣는다.
-- 서울 허브 배송담당자 두 명에게 측정용 Slack 이메일을 단다. 이메일이 없으면 order가 DM을 건너뛴다(CONTACT_NOT_CONFIGURED).
-- .invalid 도메인은 실제로 존재할 수 없는 주소다(RFC 2606). docker-compose.order-dm.yml의 허용 목록과 같아야 한다.
UPDATE user_schema.p_users SET slack_email = 'dmgr1@loadtest.invalid'
WHERE user_id = 'e2e00000-0000-4000-8000-000000000002';
UPDATE user_schema.p_users SET slack_email = 'dmgr2@loadtest.invalid'
WHERE user_id = 'e2e00000-0000-4000-8000-000000000003';
SELECT username, slack_email FROM user_schema.p_users WHERE slack_email LIKE '%@loadtest.invalid' ORDER BY username;
