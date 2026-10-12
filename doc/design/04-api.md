# 04. API

> 최종 수정: 2026-10-12 · 기준: Notion SA 6장(API 명세서)
> 전체 API 목록(U/H/C/P/O/D, I-01~I-13)은 Notion이 원본. 이 문서는 **구현된 API**만 코드 기준으로 적는다.

## 1. 공통 규약 (SA 6.1) — common 모듈이 구현

Base URL: `http://localhost:8080` (Gateway만 외부 공개). Swagger: `http://localhost:8080/swagger-ui.html`

### 요청 헤더
| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | Public API 제외 필수 | `Bearer {accessToken}` |
| `Content-Type` | Body가 있으면 필수 | `application/json` |

### Gateway → 서비스 전달 헤더 (클라이언트가 보낸 같은 이름 헤더는 제거 후 재주입)
| 헤더 | 예시 | 서비스에서 |
|---|---|---|
| `X-User-Id` | `3fa85f64-...` | `UserContext.userId()` |
| `X-Username` | `hong01` | `UserContext.username()`, Audit `created_by` |
| `X-User-Role` | `MASTER`, `USER` | `UserContext.role()`, `@RequireRole` |
| `X-Delivery-Manager` | `true`, `false` | `UserContext.isDeliveryManager()` |

### 성공 응답 (`ApiResponse`)
```json
{ "status": 200, "message": "SUCCESS", "data": { } }
```

### 목록 응답 (`ApiResponse<PageResponse>`)
```json
{ "status": 200, "message": "SUCCESS",
  "data": { "content": [ ], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5, "sort": "createdAt,DESC" } }
```
검색 공통 파라미터: `page`(0부터), `size`(10/30/50, 그 외 10), `sort`(`필드,ASC|DESC`, 기본 `createdAt,DESC`, 허용 필드는 API별)

### 에러 응답 (`ErrorResponse`)
```json
{ "status": 400, "code": "VALIDATION_ERROR", "message": "요청 값이 올바르지 않습니다.",
  "path": "/api/v1/auth/signup", "timestamp": "2026-10-13T10:15:30",
  "errors": [ { "field": "username", "reason": "4~10자의 소문자와 숫자만 사용할 수 있습니다." } ] }
```
`errors`는 `VALIDATION_ERROR`일 때만 포함. Gateway가 직접 거절하는 경우(401·404·503)도 같은 형식.

### 공통 에러 코드 (`CommonErrorCode`)
| HTTP | code | 언제 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation 실패, JSON·Enum·타입 오류, 필수 파라미터 누락 |
| 400 | `INVALID_REQUEST` | 비즈니스 규칙 위반 |
| 401 | `UNAUTHORIZED` | 토큰 누락·위조·만료, 사용자 헤더 없음 |
| 401 | `TOKEN_ROLE_MISMATCH` | 토큰 role ≠ 저장된 role, 탈퇴 사용자 |
| 403 | `FORBIDDEN` | `@RequireRole` 불일치, 소유자 아님 |
| 404 | `RESOURCE_NOT_FOUND` | 없는 경로, `/internal/**` 외부 호출 |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | |
| 503 | `SERVICE_UNAVAILABLE` | 하위 서비스 호출 실패·타임아웃, Gateway의 user-service 조회 실패 |
| 500 | `INTERNAL_ERROR` | 처리되지 않은 예외 |

리소스별 코드(`USER_NOT_FOUND`, `STOCK_INSUFFICIENT` ...)는 서비스별 `{Service}ErrorCode` enum.

### 내부 API 공통
- 경로 `/internal/**`, Gateway에 라우트 없음 + 필터에서 404 → 외부 호출 불가
- 응답은 래퍼 없이 DTO 그대로, 에러는 위 `ErrorResponse` 형식

## 2. user-service

### 구현 현황
| ID | Method | URL | 권한 | 상태 |
|---|---|---|---|---|
| U-01 | POST | `/api/v1/auth/signup` | Public | 완료 |
| U-02 | POST | `/api/v1/auth/login` | Public | 완료 |
| U-03 ~ U-08 | | `/api/v1/users/**` | | user-service 담당자 |
| I-01 | GET | `/internal/users/{userId}` | 내부 | 완료 |

### U-01. 회원가입
`POST /api/v1/auth/signup` · Public · 가입 즉시 활성화, 권한 `USER`

| 파라미터 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `username` | String | O | `^[a-z0-9]{4,10}$` |
| `password` | String | O | 8~15자, 대문자·소문자·숫자·특수문자 각 1자 이상 |
| `name` | String | O | 최대 50자 |
| `slackId` | String | O | 최대 50자 |
| `affiliationType` | String | O | `COMPANY`, `HUB` |
| `affiliationName` | String | O | 최대 100자 |

Response `201 Created`
```json
{ "status": 201, "message": "회원가입이 완료되었습니다.",
  "data": { "userId": "3fa85f64-...", "username": "hong01", "role": "USER", "isDeliveryManager": false } }
```
Error: 400 `VALIDATION_ERROR` · 409 `DUPLICATE_USERNAME` (탈퇴한 사용자의 username 포함)

### U-02. 로그인
`POST /api/v1/auth/login` · Public

| 파라미터 | 타입 | 필수 |
|---|---|---|
| `username` | String | O |
| `password` | String | O |

Response `200 OK`
```json
{ "status": 200, "message": "로그인에 성공했습니다.",
  "data": { "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 3600 } }
```
Error: 400 `VALIDATION_ERROR` · 401 `LOGIN_FAILED` (없는 사용자·탈퇴 사용자·비밀번호 불일치 구분 없음)

### I-01. 사용자 조회 (내부)
`GET /internal/users/{userId}` · 호출자: gateway(권한 재확인), company, delivery

Response `200 OK` (래퍼 없음)
```json
{ "userId": "3fa85f64-...", "username": "hong01", "role": "USER", "isDeliveryManager": false, "slackId": "U01ABCDE2FG" }
```
Error: 404 `USER_NOT_FOUND` (없거나 탈퇴)

## 3. hub-service · company-service · order-service · delivery-service

서비스 담당자가 Notion SA 6.5~6.8 기준으로 구현하면서 이 문서에 추가한다.
