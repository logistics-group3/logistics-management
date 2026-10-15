# 스텝 9. Gateway를 Spring Security(WebFlux)로 전환

- 날짜: 2026-10-13
- 계기: "Spring Security는 Gateway와 user-service 중 어디에 두나"라는 질문([노트](../qna/2026-10-13-spring-security-placement-in-msa.md))을 확인하다가, SA는 Gateway에 Spring Security를 두는데 스텝 6 구현은 직접 짠 WebFilter였다는 점을 발견했다.
- 결과: 빈 DB부터 `script/e2e-check.sh --up --with-cache-ttl` PASS 14 / FAIL 0 (126초). Security 통합 테스트 8개, 캐시 테스트 3개 통과. Swagger 문서 접근 정상
- 결정 기록: [ADR 0005](../../../decisions/0005-gateway-spring-security.md)

## 전환 전 확인: 권한 캐시와 만료 후 재조회
요청받은 대로 전환 전에 기존 구현을 실제 컨테이너에서 확인했다.

| 시점 | 상황 | 결과 |
|---|---|---|
| t+0초 | USER로 로그인 후 호출(캐시 적재) | 200 |
| t+0초 | DB role을 MASTER로 변경 직후 | 200 (캐시된 USER 사용) |
| t+30초 | 캐시 유지 중 | 200 |
| t+63초 | 캐시 만료 → user-service 재조회 → 토큰 role(USER)과 저장 role(MASTER) 불일치 | 401 TOKEN_ROLE_MISMATCH |
| 이후 | 다시 로그인한 MASTER 토큰으로 MASTER 전용 API | 200 |

이 시나리오를 `--with-cache-ttl` 옵션으로 e2e에 넣었고, 전환 뒤에도 같은 결과를 확인했다. 단위 테스트에서는 Caffeine `Ticker`로 시간을 흘려 59초(캐시 사용)와 61초(재조회)를 검증한다.

## 구조 (SA 8.3 단계 → 담당 클래스)
```
요청
 │ 1~2  UserHeaderSanitizingFilter    WebFilter, HIGHEST_PRECEDENCE. X-User-* 제거, /internal 404
 ▼
 Spring Security WebFilterChainProxy (order -100)
 │ 3    securityMatcher               화이트리스트는 체인 적용 대상에서 제외
 │ 4    oauth2ResourceServer().jwt()  NimbusReactiveJwtDecoder(HS256)로 서명·만료 검증
 │ 5    UserVerifyingJwtConverter     I-01 재확인(CachedUserLookup 60초), principal=UserSnapshot
 │      JsonAuthenticationEntryPoint  실패를 401 UNAUTHORIZED / 401 TOKEN_ROLE_MISMATCH / 503으로
 │ 6    UserHeaderInjectionFilter     AUTHORIZATION 단계 뒤, SecurityContext의 사용자로 헤더 주입
 ▼
 Gateway 라우팅 (lb://서비스)
```

## 바뀐 파일 (`gateway-service/.../gateway/security/`)
| 파일 | 상태 |
|---|---|
| `SecurityConfig` | 새로 작성. `SecurityWebFilterChain`, `ReactiveJwtDecoder` |
| `UserHeaderSanitizingFilter`, `UserVerifyingJwtConverter`, `UserHeaderInjectionFilter`, `JsonAuthenticationEntryPoint`, `GatewayAuthenticationException` | 새로 작성 |
| `AuthenticationFilter`, `JwtVerifier`, `TokenClaims` | 삭제 (역할이 위 클래스와 Spring Security로 옮겨감) |
| `CachedUserLookup`, `UserServiceClient`, `UserLookup` 등 | `auth` 패키지에서 `security` 패키지로 이동. 캐시는 테스트용 Ticker 생성자 추가 |
| `../../../../build.gradle` | jjwt 제거, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-security-test` 추가 |

## 리뷰 포인트
1. **화이트리스트를 permitAll이 아니라 securityMatcher에서 제외**: `permitAll`이어도 Authorization 헤더가 있으면 Bearer 인증 필터가 먼저 토큰을 검증한다. 그러면 만료된 토큰이 남아 있는 상태의 로그인 요청이 401이 된다. 테스트로 확인했다(`whitelistBypassesSecurity`).
2. **재확인 위치는 JWT → Authentication 변환기**: Spring Security가 서명·만료를 검증한 뒤 호출하는 확장 지점이다. 여기서 실패를 `AuthenticationException`으로 던지면 진입점이 응답을 만든다.
3. **`AuthenticationServiceException`을 쓰지 않은 이유**: 이 예외는 진입점으로 가지 않고 다시 던져져 500이 된다. 그래서 응답 코드를 담은 `GatewayAuthenticationException`(일반 `AuthenticationException`)을 만들었다.
4. **무상태 설정**: 세션 저장소(`NoOpServerSecurityContextRepository`), CSRF, 폼 로그인, Basic 인증을 끈다. Gateway는 매 요청 토큰만 본다.
5. **리액티브 SecurityContext**: 6단계 필터는 `ReactiveSecurityContextHolder`에서 사용자를 꺼낸다. 서블릿처럼 ThreadLocal이 아니라 Reactor Context에 담긴다.
6. **발급과 검증 라이브러리가 다름**: user-service는 JJWT로 서명하고, Gateway는 Nimbus로 검증한다. JWT는 표준(RFC 7519, JWS HS256)이라 라이브러리가 달라도 호환된다.
7. **테스트 방식**: `@SpringBootTest` + `forward:/echo` 라우트로 실제 Security 체인과 라우팅을 거친 뒤, echo 핸들러가 받은 헤더를 돌려준다. config server와 Eureka는 끄고 `UserLookup`은 `@MockitoBean`으로 바꾼다.

## 면접 포인트
- Spring Security(WebFlux) 필터 체인 구조와 커스텀 필터를 끼우는 위치(`addFilterAfter`, `SecurityWebFiltersOrder`)
- 인증 실패(401)와 인가 실패(403)를 처리하는 컴포넌트(AuthenticationEntryPoint, AccessDeniedHandler)
- permitAll과 security matcher 제외의 차이
