# user-service agent.md

> 이 서비스의 **협업 창구**다. 사람·AI 모두 이 서비스 코드를 수정하기 전에 루트 `CLAUDE.md`와 이 파일을 먼저 읽는다.

## 1. 서비스 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정) |
| 포트 / 스키마 / DB 계정 | 8081 / `user_schema` / `user_app` |
| 책임 | 회원가입, 로그인(JWT 발급), 사용자 관리, 권한·배송담당자 지정(소속 허브 포함) |
| 소유 테이블 | `p_users` |

## 2. 제공 API (계약)
- 외부: U-01 회원가입, U-02 로그인, U-03 내 정보, U-04 단건, U-05 검색(MASTER), U-06 수정(MASTER), U-07 권한·배송담당자 지정(MASTER), U-08 비활성화(MASTER)
- 내부: **I-01** `GET /internal/users/{userId}` → `userId, username, role, isDeliveryManager, hubId, slackId` (탈퇴 시 404)
  - 호출자: **gateway**(매 요청 권한 재확인), **company**(업체 관리자 확인), **delivery**(배송담당자 확인)
  - ⚠️ I-01 응답 필드를 바꾸면 위 3곳이 모두 깨진다. 변경 전 로그에 `OPEN`으로 올리고 합의.

## 3. 의존 (이 서비스가 호출하는 내부 API)
| API | 제공 | 용도 |
| --- | --- | --- |
| I-02 `GET /internal/hubs/{hubId}` | hub-service | U-07 배송담당자 지정 시 소속 허브 존재 확인 |

## 4. 서비스 고유 규칙
- 회원가입 권한은 항상 `USER`. 최초 MASTER는 기동 시 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`로 생성.
- 권한은 `MASTER`/`USER` 2단계. 배송담당자는 `is_delivery_manager` 속성 + `hub_id`(지정 시 필수).
- 로그인 실패는 아이디·비밀번호 구분 없이 401 `LOGIN_FAILED`.
- username 유니크는 부분 인덱스(`db/indexes.sql`) — 탈퇴한 username은 재사용 가능.
- JWT 계약은 gateway `JwtVerifier`와 같아야 한다 (HS256, `sub`=userId, `username`, `role`, `iat`, `exp`, 60분).
- 권한·탈퇴 변경은 Gateway 캐시로 최대 60초 늦게 반영된다.

## 5. 구현 현황
- [x] U-01, U-02, U-03, U-08, I-01 (공통 인프라 단계)
- [ ] U-04 단건 조회 (본인·MASTER)
- [ ] U-05 검색 (QueryDSL, 페이징)
- [ ] U-06 정보 수정
- [ ] U-07 권한·배송담당자 지정 (I-02 Feign 필요)

## 6. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `USER-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 서비스에 요청할 때는 **그 서비스의 agent.md**에 작성

```
### USER-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### USER-001 JWT 키 관리 방식
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: `JWT_SECRET`, gateway-service (GATEWAY-002)
- 내용: 현재 `.env`의 Base64 HS256 키를 user(발급)·gateway(검증)가 공유한다. 팀 논의 후 방식 확정 예정.
- 결정:

### USER-002 회원가입 입력 형식 규칙
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: U-01 `SignupRequest`
- 내용: 현재 SA 기준(username 4~10자)만 검증한다. 문자 구성(소문자·숫자 등), 비밀번호 길이·조합 규칙은 미정.
- 결정:

### USER-003 U-08을 공통 인프라 단계에서 선구현
- 상태: OPEN (리뷰 확인 필요)
- 작성: 공통 인프라 / 2026-10-13
- 관련: U-08, `@MasterOnly`
- 내용: 완료 기준 "일반 사용자가 관리자 API 호출 시 403" 검증용으로 U-08(MASTER 전용 논리 삭제)을 미리 구현했다. 공통 인프라 범위 문서상 U-03~U-08은 제외 범위였으므로 리뷰에서 유지 여부 확인.
- 결정:
