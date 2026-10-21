---
title: Gateway와 user-service의 인증·인가 흐름
date: 2026-10-19
updated: 2026-10-19
asker: kmh
level: 중급
tags: [gateway, jwt, spring-security, authentication, authorization, user-service]
---

# Gateway와 user-service의 인증·인가 흐름

## 질문
API Gateway와 user-service가 인증·인가를 어떻게 나눠 맡는지, 사용자가 요청하는 시점부터 차례대로 설명해 달라.

## 짧은 답
역할은 이렇게 나뉜다.
- user-service: 토큰을 발급한다(로그인). 사용자의 현재 role과 탈퇴 여부를 알려 준다(I-01).
- Gateway: 모든 외부 요청의 토큰을 검증한다(인증). 검증한 사용자 정보를 `X-User-*` 헤더로 넣는다.
- 각 서비스: 그 헤더를 믿고 역할 검사(`@RequireRole`)와 소유자 검사를 한다(인가).

흐름은 세 구간이다.
1. 로그인: 클라이언트가 user-service에서 JWT를 받는다. Gateway는 그냥 통과시킨다.
2. 인증: 이후 요청마다 Gateway가 서명과 만료를 확인한다. 이어서 user-service I-01로 role이 바뀌었거나 탈퇴했는지 다시 확인한다. 이 결과는 60초 캐시한다.
3. 인가: 하위 서비스가 Gateway가 넣은 헤더로 권한을 판단한다.

## 자세히

### 전체 그림
```
[클라이언트]
   │ ① POST /api/v1/auth/login {username, password}
   ▼
[Gateway :8080] ── 1~2단계 UserHeaderSanitizingFilter: X-User-* 제거, /internal/** 404
   │               3단계 화이트리스트(/auth/login, /auth/signup) → Security 체인 건너뜀
   ▼
[user-service] ── BCrypt 비교 → JWT(HS256) 발급 {sub=userId, username, role, iat, exp(1시간)}
   │
   ▼  { accessToken, expiresIn }
[클라이언트]
   │ ② GET /api/v1/users/me   Authorization: Bearer <JWT>
   ▼
[Gateway]
   1~2단계 헤더 제거, /internal 차단
   4단계 ReactiveJwtDecoder: 서명·만료·필수 claim 검증          실패 → 401 UNAUTHORIZED
   5단계 UserVerifyingJwtConverter: I-01로 role·탈퇴 재확인(60초 캐시)
         ├ 없음(탈퇴) / role 불일치                               → 401 TOKEN_ROLE_MISMATCH
         └ user-service 장애·3초 초과                             → 503 SERVICE_UNAVAILABLE
   6단계 UserHeaderInjectionFilter: X-User-Id, X-Username, X-User-Role, X-Delivery-Manager 주입
   라우팅 lb://user-service (Eureka)
   ▼
[user-service] (common 모듈)
   @RequireRole → RequireRoleInterceptor: 헤더 role 검사                실패 → 403 FORBIDDEN
   @CurrentUser → CurrentUserArgumentResolver: 헤더 → UserContext
   서비스 계층: user.validateSelfOrMaster(ownerId) 같은 소유자 검사      실패 → 403 FORBIDDEN
```

### 0. 시작 전: 공유 비밀키
- user-service와 Gateway는 같은 `JWT_SECRET`을 가진다(`config-repo`의 `jwt.secret: ${JWT_SECRET}`, 값은 `.env`).
- HS256은 대칭키 방식이다. 같은 키로 서명하고 같은 키로 검증한다. 그래서 Gateway는 user-service에 묻지 않고도 토큰이 위조됐는지 알 수 있다.

### 1. 회원가입·로그인 (토큰 발급)
1. 클라이언트가 `POST /api/v1/auth/login`을 Gateway(8080)로 보낸다.
2. Gateway의 `UserHeaderSanitizingFilter`가 가장 먼저 실행된다(`@Order(HIGHEST_PRECEDENCE)`). 클라이언트가 보낸 `X-User-*` 헤더를 지운다. 이 단계는 모든 요청에 실행된다.
3. 이 경로는 `auth.whitelist`에 있다. 그래서 `SecurityConfig`의 `securityMatcher`가 Security 체인 자체를 건너뛴다.
   - `permitAll`이 아니라 체인에서 아예 제외하는 이유가 있다. `permitAll`이면 만료된 토큰이 붙은 로그인 요청도 토큰 검증에 걸려 401이 나기 때문이다.
4. 라우트 `/api/v1/auth/**`를 따라 Eureka에서 user-service 인스턴스를 찾아 전달한다.
5. user-service `AuthService.login`이 BCrypt로 비밀번호를 비교한다.
   - 없는 사용자, 탈퇴한 사용자, 비밀번호 불일치는 모두 같은 `LOGIN_FAILED`로 응답한다. 계정이 있는지 노출하지 않기 위해서다.
   - 사용자가 없을 때도 더미 해시와 비교한다. 비교를 건너뛰면 응답이 빨라져서 응답 시간으로 계정 존재를 알아낼 수 있기 때문이다.
6. `JwtProvider`가 JWT를 만든다. claim은 `sub`(userId), `username`, `role`, `iat`, `exp`(발급 후 3600초)다.
   - payload는 서명만 되고 암호화되지 않는다. 그래서 비밀번호나 개인정보는 넣지 않는다.

### 2. 이후 요청: Gateway의 인증 (SA 8.3의 6단계)
클라이언트가 `Authorization: Bearer <JWT>`를 붙여 `GET /api/v1/users/me`를 보낸다고 하자.

| 단계 | 담당 | 하는 일 | 실패 응답 |
|---|---|---|---|
| 1 | `UserHeaderSanitizingFilter` | 클라이언트가 보낸 `X-User-*` 헤더 제거 | - |
| 2 | `UserHeaderSanitizingFilter` | `/internal/**` 경로 차단 | 404 |
| 3 | `SecurityConfig.securityMatcher` | 화이트리스트면 인증 생략 | - |
| 4 | `oauth2ResourceServer` + `ReactiveJwtDecoder` | 서명(HS256), 만료(허용 오차 0), 필수 claim 5개 검증 | 401 `UNAUTHORIZED` |
| 5 | `UserVerifyingJwtConverter` | I-01로 저장된 role, 탈퇴 여부 재확인 | 401 `TOKEN_ROLE_MISMATCH`, 503 |
| 6 | `UserHeaderInjectionFilter` | 검증된 값으로 `X-User-*` 헤더 주입 | - |

**1단계가 가장 중요한 이유:** 하위 서비스는 `X-User-*` 헤더를 검증 없이 믿는다. Gateway가 클라이언트 값을 먼저 지우지 않으면, 누구나 `X-User-Role: MASTER`를 붙여 관리자가 될 수 있다.

**4단계 세부:** 기본 검증기는 `exp`가 있을 때만 만료를 검사하고, 만료 뒤 60초까지 허용한다. `exp`가 빠진 토큰이 영원히 유효해지지 않도록 필수 claim이 있는지 직접 검사하고, 허용 오차는 0으로 뒀다.

**5단계가 필요한 이유:** JWT는 발급 시점의 정보를 담은 자기완결형 토큰이다. 발급 뒤에 MASTER가 role을 바꾸거나 사용자가 탈퇴해도, 토큰은 만료될 때(최대 1시간)까지 옛 정보를 그대로 들고 있다. 그래서 Spring Security가 JWT를 `Authentication`으로 바꾸는 지점에 재확인을 끼워 넣었다.
- Gateway가 WebClient로 user-service의 `GET /internal/users/{userId}`(I-01)를 부른다. WebFlux라서 블로킹인 Feign 대신 WebClient를 쓴다.
- 탈퇴한 사용자는 `@SQLRestriction("deleted_at IS NULL")` 때문에 조회되지 않아 404가 된다. Gateway는 404를 "사용자 없음"으로 바꿔 `TOKEN_ROLE_MISMATCH`로 응답한다.
- 토큰의 role과 DB의 role이 다를 때도 `TOKEN_ROLE_MISMATCH`다. 응답 문구는 "다시 로그인해 주세요"다.
- user-service가 죽었거나 3초 안에 응답하지 않으면 503이다.
- `CachedUserLookup`(Caffeine)이 결과를 60초 동안 캐시한다. 사용자당 user-service 호출이 1분에 한 번으로 줄어드는 대신, 권한 변경은 최대 60초 늦게 반영된다. 같은 사용자의 동시 요청은 조회 하나를 공유한다. 실패한 결과는 캐시하지 않는다.

**6단계 세부:**
- 헤더 값은 토큰 claim이 아니라 I-01로 확인한 `UserSnapshot`에서 꺼낸다.
- `X-Delivery-Manager`는 JWT에 없는 값이라 I-01 응답에서만 얻을 수 있다.
- 인증 정보는 `ThreadLocal`이 아니라 `ReactiveSecurityContextHolder`(Reactor Context)에서 꺼낸다.
- 세션은 쓰지 않는다(`NoOpServerSecurityContextRepository`). 매 요청을 토큰만으로 판단하는 무상태 구조다.

### 3. 하위 서비스의 인가 (common 모듈)
user-service뿐 아니라 hub, company, order, delivery도 같은 방식이다.
- `@RequireRole(Role.MASTER)`: `RequireRoleInterceptor`가 컨트롤러 실행 전에 `X-User-Role`을 검사한다. 맞지 않으면 403 `FORBIDDEN`이다.
- `@CurrentUser UserContext user`: `CurrentUserArgumentResolver`가 헤더를 `UserContext`로 바꿔 파라미터에 넣는다. 필수 헤더(id, username, role)가 없거나 형식이 틀리면 401이다.
- 소유자 검사는 데이터를 봐야 알 수 있어서 서비스 계층에서 한다. 예: `user.validateSelfOrMaster(order.getOrdererId())`.
- 서비스끼리 Feign으로 호출할 때는 `FeignHeaderInterceptor`가 `X-User-*`를 그대로 넘긴다. 그래서 하위 서비스도 감사 컬럼(`created_by`)을 채울 수 있다.

### 왜 헤더를 믿어도 되나: 전제 조건
- 서비스 포트(8081~8085)는 호스트에 공개하지 않는다. `docker-compose.yml`에서 외부에 열린 포트는 Gateway 8080뿐이다. 외부에서 서비스에 닿는 길은 Gateway 하나다.
- Gateway가 클라이언트 헤더를 지우고 자기가 검증한 값만 넣는다.
- 이 두 전제 중 하나라도 깨지면(서비스 포트가 열리거나, 헤더 제거가 빠지면) 헤더 위조로 권한을 얻을 수 있다.

### 인증과 인가의 경계
| | 인증(Authentication): 누구인가 | 인가(Authorization): 무엇을 할 수 있나 |
|---|---|---|
| 담당 | Gateway (+ user-service I-01) | 각 서비스 |
| 근거 | JWT 서명, I-01 확인 결과 | `X-User-Role`, 리소스 소유자 |
| 실패 | 401 | 403 |

Gateway에서는 "로그인한 사용자인가"만 본다(`anyExchange().authenticated()`). "MASTER만 가능한가" 같은 규칙은 업무 지식이라 각 서비스에 둔다. Gateway에 경로별 역할 규칙을 몰아넣으면 서비스가 API를 바꿀 때마다 Gateway도 같이 바꿔야 한다.

### 더 깊이
- 대칭키(HS256)와 비대칭키(RS256) 비교: RS256은 user-service만 개인키로 서명하고, Gateway는 공개키로 검증만 한다. 키가 유출됐을 때 피해 범위가 작다. 이 프로젝트는 단순함을 위해 HS256을 쓴다.
- Refresh Token이 없으므로 토큰이 만료되면(1시간) 다시 로그인해야 한다. 로그아웃이나 강제 만료는 토큰 블랙리스트 없이 5단계 재확인(탈퇴, role 변경)으로 일부만 대신한다.

## 면접 포인트
- "JWT를 쓰는데 권한이 바뀌면 어떻게 하나": JWT는 발급 뒤의 변경을 모른다. Gateway가 매 요청 user-service에 role과 탈퇴 여부를 재확인하고, 부하를 줄이려고 60초 캐시한다. 반영 지연 60초와 호출량 감소를 맞바꾼 트레이드오프다.
- "하위 서비스는 사용자를 어떻게 아나": Gateway가 클라이언트 헤더를 지우고 검증된 `X-User-*`를 넣는다. 서비스 포트를 외부에 열지 않는다는 전제에서만 안전하다.
- "인증은 Gateway, 인가는 서비스로 나눈 이유": 토큰 검증은 모든 요청에 공통이라 관문에서 한 번만 한다. 역할·소유자 규칙은 도메인 지식이라 서비스에 둔다.
- 꼬리 질문: 로그인 실패 메시지를 왜 하나로 통일했나. 계정 존재 여부 노출(사용자 열거 공격)을 막기 위해서다. 응답 시간 차이까지 더미 해시 비교로 없앴다.
