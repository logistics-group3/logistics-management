# user 협업 창구

회원과 인증을 맡는 user-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`user-service/AGENTS.md`](../../../user-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 권민혁 |
| 포트 | 8081 |
| 스키마·DB 계정 | `user_schema` / `user_app` |
| 책임 | 회원가입, 로그인(JWT 발급), 사용자 관리, 배송담당자 지정(소속 허브 포함) |
| 소유 테이블 | `p_users` |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| U-01 | `POST /api/v1/auth/signup` | Public | 구현 |  |
| U-02 | `POST /api/v1/auth/login` | Public | 구현 | JWT HS256 60분, claim `sub`(userId), `username`, `role` |
| U-03 | `GET /api/v1/users/me` | 로그인 | 구현 |  |
| U-04 | `GET /api/v1/users/{userId}` | 본인, MASTER | 구현 | 권한 검사를 조회보다 먼저 해 USER는 없는 ID에도 403(존재 여부 비노출) |
| U-05 | `GET /api/v1/users` | MASTER | 구현 | 정렬 허용 `createdAt`, `username`. 탈퇴자 제외 |
| U-06 | `PATCH /api/v1/users/{userId}` | MASTER | 구현 | SA에 없는 규칙: 배송담당자의 `affiliationType`·`affiliationName` 수정은 400 `INVALID_REQUEST`, 공백만 있는 값은 400 `VALIDATION_ERROR` ([decisions/0001](decisions/0001-user-management-guards.md)) |
| U-07 | `PATCH /api/v1/users/{userId}/role` | MASTER | 구현 | SA에 없는 규칙: 자기 role 변경 400 `INVALID_REQUEST`, 해제하며 hubId 전송·비담당자에게 hubId만 전송 400 `VALIDATION_ERROR` ([decisions/0001](decisions/0001-user-management-guards.md)). hub I-02 구현 전에는 허브 지정이 404 `NOT_FOUND` |
| U-08 | `DELETE /api/v1/users/{userId}` | MASTER | 구현 | 자기 자신 삭제 400 `INVALID_REQUEST`(SA와 같음) |
| I-01 | `GET /internal/users/{userId}` | gateway, company, delivery | 구현 | DTO 그대로 반환(ADR 0010). 탈퇴 시 404 `USER_NOT_FOUND` |
| I-14 | `GET /internal/users/delivery-managers?hubId=` | delivery | 구현 | `[{userId, slackId}]`를 감싸지 않고 반환. 없으면 `[]`, hubId 누락 400. 정렬하지 않는다(동점 처리는 delivery) |
| I-16 | `GET /internal/users/{userId}/notification-contact` | order | 로컬 구현·팀 리뷰 대기 | `{userId, isDeliveryManager, slackEmail}` 그대로 반환. 없음·탈퇴 404 USER_NOT_FOUND, 이메일 미등록은 null. [ADR 0016](../../decisions/0016-delivery-manager-slack-dm.md) |

ADR 0016의 로컬 확장: U-01은 필수 `slackEmail`(이메일 형식, 최대 254자)을 받고 slackId 입력을 제거한다. 이메일 없는 이전 가입 요청은 400이므로 소비자가 함께 변경해야 한다. U-06(MASTER)은 생략·null이면 유지, 빈 문자열이면 해제한다. UserResponse(U-03~U-07)는 등록된 이메일만 추가하며 null이면 필드를 생략한다. V3가 slack_id의 NOT NULL을 해제한다. I-01·I-14의 필드 구성은 유지하지만 신규 사용자의 slackId는 null이다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-02 `GET /internal/hubs/{hubId}` | hub | 배송담당자 지정 시 소속 허브 확인과 허브명(U-07). 응답에서 `hubId`, `name`만 쓴다. `HUB_NOT_FOUND`는 그대로 전달 | GET이라 가능(`IdempotentRetryConfig`) |

## 4. 구현 현황

- [x] U-01 회원가입, U-02 로그인, I-01 내부 조회 (통합 테스트, E2E)
- [x] 기동 시 MASTER 계정 자동 생성(`MASTER_USERNAME`/`MASTER_PASSWORD`)
- [x] U-03 내 정보 조회, U-04 단건 조회 (단위·WebMvc·통합 테스트, Gateway 경유 호출)
- [x] U-05 검색, U-06 수정, U-07 권한·배송담당자 지정, U-08 논리 삭제 (단위·WebMvc·통합 테스트, Gateway 경유 호출. U-07 허브 지정의 hub 실제 연동은 hub I-02 구현 후 확인)
- [x] I-14 허브별 배송담당자 목록 (통합 테스트, 내부 호출)
- [x] slackEmail·Flyway V2/V3·I-16·U-01/U-06 연동. user 테스트 88개, 실제 Gateway 가입·MASTER 설정·해제·USER 거절, 실제 I-16과 DM 확인(2026-10-16)
- [x] 공통 인프라 샘플 API 삭제, `script/e2e-check.sh`를 U-03·U-05로 교체

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### USER-001 I-14 허브별 배송담당자 목록 구현과 응답 확인 요청
- 상태: OPEN
- 작성: delivery / 한얼 / 2026-10-15
- 관련: I-14, I-01, SA 6.3, delivery I-09 자동 배정, D-04 재배정
- 내용: delivery는 배송 생성(I-09) 때 구간 담당 허브마다 I-14를 호출해 자동 배정 후보를 받는다. delivery 쪽 Feign은 `GET /internal/users/delivery-managers?hubId=` → `[{ userId, slackId }]`(탈퇴자·배송담당자 아닌 사용자 제외)로 구현했다. 후보가 없으면 빈 배열을 기대한다(404가 아니라 빈 배열이어야 delivery가 `DELIVERY_MANAGER_NOT_FOUND`로 판단한다). I-14가 아직 없어 I-09가 실제 연동에서는 실패한다. 구현 일정과 빈 배열 응답 여부를 알려 달라. I-01(`userId, isDeliveryManager, hubId, slackId`)은 이미 있어 그대로 쓴다.
- 결정:

### USER-002 배송담당자 Slack 이메일과 내부 연락처 조회

- 상태: OPEN (기능 구현 요청에 따른 로컬 변경, 제공 담당자 리뷰 대기)
- 작성: order / Junhyun An 작업 / 2026-10-15
- 내용: 사용자의 이메일 DM 구현 요청에 따라 slackEmail·U-01/U-06·I-16을 추가한다. 신규 가입은 이메일만 필수로 받고 ID를 받지 않도록 10/16 후속 요청을 반영했다. 기존 I-01·I-14의 필드 구성은 유지하며 이메일은 새 연락처 조회로만 내부 제공한다.
- 관련: [ADR 0016](../../decisions/0016-delivery-manager-slack-dm.md). 팀 전체 승인·제공 담당자의 검토 완료를 뜻하지 않는다.
- 구현·검증: 2026-10-16 로컬 구현과 user 테스트 88개 통과. `.env`에 이메일을 넣는 대신 사용자 데이터의 선택 연락처로 저장한다. V2/V3 적용·U-01 필수 이메일·I-01/I-14의 nullable slackId·연락처 노출 범위를 제공 담당자가 리뷰한다. [실행 안내](../../guides/slack-dm.md)

### USER-003 권한 변경·탈퇴 시 Gateway Redis 권한 캐시 키 삭제
- 상태: OPEN (성능 측정용 로컬 구현, 제공 담당자 리뷰 대기)
- 작성: kmh / 2026-10-19
- 내용: U-07(권한·배송담당 변경)과 U-08(탈퇴)이 `UserAuthChangedEvent`를 내고, 커밋 뒤 `user.auth-cache.type=redis`일 때만 `auth:user:{userId}` 키를 지운다. 기본 `local`에서는 아무것도 하지 않는다. 삭제 실패는 로그만 남기고 Gateway TTL(60초)로 반영된다.
- 관련: [ADR 0019](../../decisions/0019-redis-cache-measured-adoption.md), GATEWAY-001.
