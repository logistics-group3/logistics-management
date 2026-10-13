# 0003. 인증 관련 라이브러리와 구성

- 상태: 채택
- 날짜: 2026-10-13
- 결정자: junhyun (공통 인프라 개인 구현안)

## 맥락
- SA 8장: user-service가 HS256 JWT를 발급하고, Gateway가 검증한다. 서비스는 Gateway가 넣은 X-User-* 헤더로 역할을 확인한다.
- 버전은 Boot 3.5.16 기준으로 Maven Central에서 확인했다(2026-10-13).

## 결정
- JWT: `io.jsonwebtoken:jjwt-*:0.13.0`(최신). 버전은 `gradle.properties`의 `jjwtVersion`
- 비밀번호 해시: `spring-security-crypto`의 `BCryptPasswordEncoder`만 쓴다. `spring-boot-starter-security`는 넣지 않는다.
- Gateway: Spring Security 없이 `GlobalFilter`로 SA 8.3 순서를 직접 구현한다. 권한 재확인 캐시는 Caffeine(Boot BOM 관리 버전)
- 서비스 내부 인가: common의 `@RequireRole` 인터셉터

## 검토한 대안
- spring-boot-starter-security(서비스): 기본 필터 체인이 모든 요청을 막아 설정이 늘어난다. 인증은 Gateway가 이미 끝냈으므로 서비스에 필요 없다.
- Spring Security(WebFlux)로 Gateway 구성: SecurityWebFilterChain, ReactiveAuthenticationManager 등 개념이 많아 반나절 안에 SA 순서(헤더 제거, internal 차단, 재확인)를 정확히 맞추기 어렵다. 필요하면 채택 후 전환한다.
- JJWT 0.12.x: 0.13.0과 API가 같고 더 오래됐다.

## 결과
- user-service와 gateway가 같은 `JWT_SECRET`을 환경 변수로 공유해야 한다.
- 서비스가 헤더를 그대로 믿으므로, 서비스 포트를 외부에 공개하지 않는 배포 구성이 전제다.
