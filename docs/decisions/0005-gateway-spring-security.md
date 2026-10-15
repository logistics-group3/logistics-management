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

## 팀원 ADR 보완: seungkeun ADR-05 인증·인가: Gateway 일괄 검증 + 권한 재확인
> 원문: 최승근, Notion "스파르타물류 기술 결정 문서 (ADR)". 원문 문장을 그대로 옮겼다. 이 ADR과 다른 점: 방향이 같다(Gateway 일괄 JWT 검증, 권한·탈퇴 재확인 60초 캐시, 하위 서비스는 `@RequireRole`). 원문은 "user-service 장애 시 Gateway 동작(권장: 거부, 503)"을 팀 확인 항목으로 남겼다.

### 트레이드오프
- 권한을 바꿔도 최대 60초는 이전 권한이 통합니다. 이번 도메인에서 허용할 수 있는 범위로 봤습니다.
- user-service가 죽으면 캐시가 없는 요청은 통과시킬지 정해야 합니다. 권장은 거부(503, fail-closed)이며, 팀 최종 확인이 필요합니다.
- 하위 서비스는 헤더를 믿으므로, 서비스 포트에 직접 접근하면 위조할 수 있습니다. 외부에는 8080(Gateway)과 8761만 열고, 운영에서는 네트워크 정책이나 mTLS로 막아야 합니다.
- Refresh 토큰은 범위에서 뺐습니다. 60분마다 다시 로그인해야 합니다.

### 면접 답변
> "JWT의 약점인 '발급 후 취소 불가'를 보완하려고, Gateway가 토큰을 검증한 뒤 user-service에 권한과 탈퇴 여부를 다시 확인합니다. 매번 부르면 부하가 커서 60초 캐시를 두었고, 그만큼의 반영 지연은 감수했습니다."
- **Q. 그러면 토큰에 role을 넣을 필요가 있나요?** 토큰의 role과 저장된 role을 비교해 불일치를 감지하는 데 씁니다. 불일치면 "권한이 바뀌었으니 다시 로그인하라"는 명확한 신호(401)를 줄 수 있습니다.
- **Q. 왜 Spring Security의 @PreAuthorize를 쓰지 않았나요?** 서비스에는 인증 기능이 필요 없고 역할 확인만 남아, 의존성과 설정을 늘리지 않는 쪽을 택했습니다. 내부망도 검증해야 하는 환경이라면 Security + 토큰 전달(B)로 가야 합니다.
