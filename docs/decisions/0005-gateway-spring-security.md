# 0005. Gateway 인증을 Spring Security(WebFlux)로 전환

- 상태: 채택 (0003의 Gateway 부분을 대체)
- 날짜: 2026-10-13
- 결정자: junhyun

## 맥락
- 0003에서는 반나절 일정 때문에 Gateway 인증을 Spring Security 없이 `WebFilter` 하나로 직접 구현했다.
- SA 1.4 기술 스택과 3.1 구성도에는 gateway-service에 Spring Security가 명시돼 있다. 0003은 이 부분에서 SA와 달랐고, 구현 전에 확인하지 않았다.

## 결정
- Gateway에 `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`를 쓴다. JWT 검증은 `NimbusReactiveJwtDecoder`(HS256)가 맡는다. JJWT는 user-service의 발급에만 남긴다.
- SA 8.3 단계별 담당:
  1~2. `UserHeaderSanitizingFilter`(Security 체인 앞, 모든 요청)
  3. `securityMatcher`에서 화이트리스트 제외
  4. `oauth2ResourceServer().jwt()`
  5. `UserVerifyingJwtConverter`(I-01 재확인, 60초 캐시)
  6. `UserHeaderInjectionFilter`(AUTHORIZATION 단계 뒤)
- 에러 응답은 `JsonAuthenticationEntryPoint`가 SA 6.1 형식으로 쓴다.
- 비즈니스 서비스는 바꾸지 않는다. SA 8.3 7단계대로 `@RequireRole` 인터셉터를 쓴다.

## 검토한 대안
- 직접 구현한 WebFilter 유지: 동작은 같지만 SA와 다르고, 토큰 검증과 예외 처리를 표준 컴포넌트에 맡기지 못한다.
- 서비스마다 리소스 서버 구성: 제로 트러스트에 가깝지만 서비스 5개 모두에 설정과 키 배포가 필요하다. SA 8.3과도 다르다.

## 결과
- 동작과 응답 계약은 그대로다. e2e(빈 DB부터) PASS 14/0, Security 통합 테스트 8개, 캐시 테스트 3개
- 화이트리스트는 `permitAll`이 아니라 Security 적용 대상에서 제외했다. 만료된 토큰이 붙은 로그인 요청이 401이 되는 문제를 피한다.
