# user 협업 창구

회원과 인증을 맡는 user-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`user-service/AGENTS.md`](../../../user-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8081 |
| 스키마·DB 계정 | `user_schema` / `user_app` |
| 책임 | 회원가입, 로그인(JWT 발급), 사용자 관리, 배송담당자 지정(소속 허브 포함) |
| 소유 테이블 | `p_users` |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| U-01 | `POST /api/v1/auth/signup` | Public | 구현 |  |
| U-02 | `POST /api/v1/auth/login` | Public | 구현 | JWT HS256 60분, claim `sub`(userId), `username`, `role` |
| U-03~U-08 | `/api/v1/users/**` | 로그인·본인·MASTER | 예정 |  |
| I-01 | `GET /internal/users/{userId}` | gateway, company, delivery | 구현 | DTO 그대로 반환(ADR 0010). 탈퇴 시 404 `USER_NOT_FOUND` |
| I-14 | `GET /internal/users/delivery-managers?hubId=` | delivery | 예정 |  |
| 샘플 | `GET /api/v1/users/sample/me`, `/admin` | 로그인, MASTER | 구현 | 공통 인프라 검증용. 업무 API가 대체하면 정리한다 |

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-02 `GET /internal/hubs/{hubId}` | hub | 배송담당자 지정 시 소속 허브 확인(U-07) | GET이라 가능 |

## 4. 구현 현황

- [x] U-01 회원가입, U-02 로그인, I-01 내부 조회 (통합 테스트, E2E)
- [x] 기동 시 MASTER 계정 자동 생성(`MASTER_USERNAME`/`MASTER_PASSWORD`)
- [ ] U-03~U-08 사용자 관리
- [ ] I-14 허브별 배송담당자 목록

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### USER-001 I-14 허브별 배송담당자 목록 구현과 응답 확인 요청
- 상태: OPEN
- 작성: delivery / 한얼 / 2026-10-15
- 관련: I-14, I-01, SA 6.3, delivery I-09 자동 배정, D-04 재배정
- 내용: delivery는 배송 생성(I-09) 때 구간 담당 허브마다 I-14를 호출해 자동 배정 후보를 받는다. delivery 쪽 Feign은 `GET /internal/users/delivery-managers?hubId=` → `[{ userId, slackId }]`(탈퇴자·배송담당자 아닌 사용자 제외)로 구현했다. 후보가 없으면 빈 배열을 기대한다(404가 아니라 빈 배열이어야 delivery가 `DELIVERY_MANAGER_NOT_FOUND`로 판단한다). I-14가 아직 없어 I-09가 실제 연동에서는 실패한다. 구현 일정과 빈 배열 응답 여부를 알려 달라. I-01(`userId, isDeliveryManager, hubId, slackId`)은 이미 있어 그대로 쓴다.
- 결정:
