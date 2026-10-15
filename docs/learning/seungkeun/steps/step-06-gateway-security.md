# 스텝 6. gateway 보안 필터

> 스텝 9에서 이 필터를 Spring Security(WebFlux) 구성으로 바꿨다. SA 단계와 응답 계약은 같다. 현재 구조는 [스텝 9](step-09-gateway-spring-security.md)를 본다.

- 날짜: 2026-10-13
- 결과: 완료 기준 3~6을 컨테이너 환경에서 curl로 확인. gateway 테스트 9개 통과

| 기준 | 요청 | 결과 |
|---|---|---|
| 3 | USER 토큰으로 `/api/v1/users/sample/me` | 200, `@CurrentUser`로 userId·username·role 수신 |
| 4 | 토큰 없음 / 서명 변조 토큰 | 401 UNAUTHORIZED |
| 4 | USER 토큰으로 `/sample/admin` | 403 FORBIDDEN (MASTER 토큰은 200) |
| 5 | `/internal/users/x` (토큰 있음) | 404 NOT_FOUND |
| 6 | USER 토큰 + `X-User-Role: MASTER` | 403, `/me`도 실제 사용자로 응답(위조 `X-Username: evil` 무시) |

## 만든 것 (`gateway-service/.../gateway/`)
| 파일 | 역할 |
|---|---|
| `auth/AuthenticationFilter` | SA 8.3 1~6단계. 메서드 안 주석 번호가 SA 단계 번호 |
| `auth/JwtVerifier` | HS256 서명·만료 검증. 실패 사유는 구분하지 않고 401 |
| `auth/UserServiceClient` | `@LoadBalanced WebClient`로 I-01 호출. 404는 탈퇴로 처리 |
| `auth/CachedUserLookup` | Caffeine `AsyncCache` 60초 TTL. 실패는 캐시하지 않음 |
| `auth/AuthConfig`, `AuthProperties`, `JwtProperties` | 빈 구성, 화이트리스트·TTL·secret 설정 |
| `support/ErrorResponseWriter`, `GatewayErrorCode` | SA 6.1 에러 JSON을 Gateway가 직접 작성 |
- 설정: `../../../../config-repo/gateway-service.yml`의 `jwt.secret`, `auth.whitelist`, `auth.user-cache-ttl`

## 리뷰 포인트
1. **WebFilter를 쓴 이유**: Spring Cloud Gateway의 `GlobalFilter`는 라우트가 매칭된 요청에만 실행된다. `/internal/**`은 라우트가 없어서 GlobalFilter로는 막을 수 없고 기본 404 형식이 나간다. `WebFilter`는 라우트 매칭 전에 모든 요청에 실행된다.
2. **헤더 제거가 1단계인 이유**: 서비스는 X-User-* 헤더를 믿는다. 화이트리스트 요청까지 포함해 모든 요청에서 먼저 지운다. 스텝 5에서 재현한 위조 헤더 통과 문제가 여기서 해결됐다.
3. **권한 재확인과 캐시**: JWT는 발급 후 바뀐 권한이나 탈퇴를 모른다. 그래서 요청마다 저장된 role과 비교하고(SA 8.2), 비용을 줄이려고 60초 캐시한다. 권한 변경은 최대 60초 늦게 반영된다(팀 결정). `AsyncCache`는 같은 사용자의 동시 요청을 조회 한 번으로 합친다.
4. **실패 분류**: 토큰 문제는 401 UNAUTHORIZED, 저장된 role과 불일치하거나 탈퇴했으면 401 TOKEN_ROLE_MISMATCH(재로그인 필요), user-service 장애는 503 SERVICE_UNAVAILABLE. 장애를 401로 응답하면 클라이언트가 멀쩡한 토큰을 버리게 된다.
5. **리액티브 체인 주의점**: `onErrorResume`을 `chain.filter()` 뒤에 두면 하위 서비스 오류까지 503으로 덮어쓴다. 그래서 조회 결과를 `Verification`으로 먼저 확정하고, 그다음에 라우팅한다.
6. **WebClient를 쓴 이유**: Gateway는 WebFlux(Netty) 기반이다. 블로킹 Feign을 이벤트 루프 스레드에서 호출하면 전체 처리량이 떨어진다.
7. **역할 확인 위치**: Gateway는 "누구인가"와 "토큰이 아직 유효한가"만 판단한다. "이 API를 쓸 수 있는가"는 서비스의 `@RequireRole`이 판단한다(403).

## 운영 중 관찰
- Gateway를 재시작한 직후에는 Eureka 목록을 받기 전까지 user-service 라우팅이 `No servers available`(503)로 실패한다. 준비 확인은 실제로 user-service를 거치는 요청(로그인)으로 해야 한다.
- `UserContext.isMaster()`가 JSON에 `master` 필드로 노출되던 문제를 `@JsonIgnore`로 고쳤다.

## 면접 포인트
- API Gateway에서 인증하고 서비스에서 인가하는 구조의 장단점, 내부망 신뢰 모델(zero trust와의 차이)
- JWT 즉시 무효화가 어려운 문제와 대안(짧은 만료 + refresh, 블랙리스트, 이 프로젝트의 재확인 캐시)
- WebFlux에서 블로킹 호출을 피해야 하는 이유
