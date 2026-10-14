# gateway-service agent.md

> 이 서비스의 **협업 창구**다. 사람·AI 모두 이 서비스 코드를 수정하기 전에 루트 `CLAUDE.md`, 이 폴더의 `CLAUDE.md`(세부 규칙), 이 파일을 먼저 읽는다.

## 1. 서비스 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정) |
| 포트 / 스키마 | 8080 (유일한 외부 진입점) / 없음 |
| 기술 | Spring Cloud Gateway (WebFlux) — `common` 모듈을 쓰지 않는다 |
| 책임 | 라우팅, JWT 검증, `X-User-*` 헤더 제거 후 재주입, `/internal/**` 차단, 권한 재확인(60초 캐시), 통합 Swagger UI |

## 2. 제공 계약
- 라우팅 표: `application.yml` (`/api/v1/auth/**`, `/api/v1/users/**` → user … `/api/v1/deliveries/**` → delivery)
- 공개 경로(토큰 불필요): `/api/v1/auth/signup`, `/api/v1/auth/login`, Swagger 관련 경로 — `AuthenticationFilter.PUBLIC_PATHS`
- 하위 서비스로 넣어 주는 헤더: `X-User-Id`, `X-Username`, `X-User-Role`, `X-Delivery-Manager` (common `UserHeaders`와 동일해야 함)
- 에러: 401 `UNAUTHORIZED`, 401 `TOKEN_ROLE_MISMATCH`, 503 `SERVICE_UNAVAILABLE` (공통 에러 형식)

## 3. 의존
| API | 제공 | 용도 |
| --- | --- | --- |
| I-01 `GET /internal/users/{userId}` | user-service | 사용자 존재·권한 재확인, 헤더 값 (`userId, username, role, isDeliveryManager`) |

## 4. 서비스 고유 규칙
- 세부 규칙은 같은 폴더의 `CLAUDE.md`에 있다 (규칙은 그곳 한 곳에만 둔다, COMMON-005).

## 5. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `GATEWAY-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 서비스에 요청할 때는 **그 서비스의 agent.md**에 작성

```
### GATEWAY-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### GATEWAY-001 `/internal/**` 차단 응답 형식
- 상태: DECIDED
- 작성: 공통 인프라 / 2026-10-13
- 관련: `AuthenticationFilter`, 라우팅 표
- 내용: 매칭 라우트가 없어 Gateway 기본 404 형식으로 응답한다(공통 에러 형식 아님).
- 결정: 외부 접근이 확실히 막히므로 현행 유지.

### GATEWAY-002 JWT 키 관리 방식
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: `JWT_SECRET`, user-service (USER-001)
- 내용: 현재 `.env`의 Base64 키를 user·gateway가 공유. 팀 논의 후 결정 예정. 논의는 `user-service/agent.md`의 USER-001에서 진행.
- 결정:
