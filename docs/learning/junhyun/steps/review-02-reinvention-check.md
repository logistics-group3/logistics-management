# 리뷰 2. 이미 있는 기능을 직접 구현했거나 하드코딩한 곳 검사 (2026-10-14)

- 요청: "이미 있는 모듈을 쓰면 되는데 직접 구현했다거나, 어노테이션으로 해결할 수 있는데 하드코딩한 곳"을 찾는다.
- 방법: 메인 에이전트가 common, gateway, user-service, 빌드·인프라 파일을 전부 읽고, 각 지점마다 Spring, Boot, Spring Cloud, Gradle이 제공하는 대안이 있는지 대조했다.
- 결론: 바꾸는 게 좋은 곳 5곳, 대안이 있지만 지금 방식이 맞는 곳 9곳, 확인 후 결정할 곳 2곳.
- 반영: A-1~A-4는 [스텝 11](step-11-review-2-fixes.md)에서 고쳤다. A-5는 남겼다.

## A. 바꾸는 게 좋은 곳
| # | 위치 | 지금 | 이미 있는 기능 | 영향 |
|---|---|---|---|---|
| 1 | `common/.../GlobalExceptionHandler` | Spring MVC 표준 예외(415, 405, 406, 404, 파라미터 검증 등)를 `ServletException` 처리기와 상태 코드 switch로 직접 매핑 | `ResponseEntityExceptionHandler`를 상속하면 표준 예외 약 20종을 올바른 상태로 처리해 준다. `handleExceptionInternal` 하나만 오버라이드해 SA 6.1 형식으로 바꾸면 된다 | 표준 예외 목록을 직접 관리해야 하고, 빠뜨린 예외는 500이 된다. 스텝 10-1에서 직접 추가한 코드라 이번 검사에서 찾은 가장 큰 항목 |
| 2 | `Dockerfile` | `COPY .../${MODULE}-0.0.1-SNAPSHOT.jar` 버전이 하드코딩됨 | Boot Gradle 플러그인의 `bootJar { archiveFileName = 'app.jar' }`로 산출물 이름을 고정 | `version`을 바꾸는 순간 이미지 빌드가 깨진다 |
| 3 | gateway 빈 5개(`UserHeaderSanitizingFilter`, `UserVerifyingJwtConverter`, `JsonAuthenticationEntryPoint`, `ErrorResponseWriter`, `GatewayErrorWebExceptionHandler`), `GatewayErrorCode` | 생성자와 getter를 손으로 작성 | Lombok `@RequiredArgsConstructor`, `@Getter @Accessors(fluent = true)` (common과 user-service는 이미 이렇게 씀) | 코딩 규칙("생성자 주입은 @RequiredArgsConstructor") 위반, 모듈마다 스타일이 다름 |
| 4 | gateway `UserHeaders`, `GatewayErrorCode` | common과 같은 상수·코드·문구를 gateway에 다시 정의 | common을 웹과 무관한 `common-core`(에러 코드, 헤더 상수)와 MVC용 `common-web`으로 나누면 gateway도 core를 재사용 | 한쪽만 고치면 서로 어긋난다. 단, 모듈 구조 변경이라 팀 결정이 필요 |
| 5 | user-service `infrastructure/security/SecurityConfig` | Spring Security를 쓰지 않는데 이름이 SecurityConfig(PasswordEncoder, Clock 빈만 있음) | 이름만 바꾸면 됨(`PasswordEncoderConfig` 등) | 이름이 Spring Security 설정으로 오해를 준다 |

## B. 대안이 있지만 지금 방식을 유지하는 곳
| 위치 | 이미 있는 대안 | 유지하는 이유 |
|---|---|---|
| Gateway 헤더 제거 필터 | Gateway 내장 `RemoveRequestHeader` 필터 | 내장 필터는 라우트가 매칭된 뒤, Security 다음에 실행된다. 그래서 인증 전 제거, 라우트 없는 요청 처리가 안 되고, 우리가 주입한 헤더까지 지운다 |
| `@RequireRole` + 인터셉터 | Spring Security `@PreAuthorize("hasRole('MASTER')")` | SA 8.3이 커스텀 어노테이션 + 인터셉터를 명시했고, 서비스에 Security 필터 체인을 들이지 않기로 했다(ADR 0003) |
| `@CurrentUser` ArgumentResolver | Spring Security `@AuthenticationPrincipal` | 같은 이유. 서비스에는 SecurityContext가 없다 |
| Gateway JWT 디코더 빈 직접 정의 | Boot 속성 `spring.security.oauth2.resourceserver.jwt.*` | Boot 속성은 JWK URI, 공개키만 지원하고 HMAC 비밀키는 지원하지 않는다. 필수 claim 검증기도 속성으로 못 건다 |
| `PageResponse` | Spring Data `PagedModel`(`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`) | 응답 모양이 SA 6.1(`page`, `size`, `sort`가 평평하게)과 다르다 |
| `SearchPageableArgumentResolver` | `spring.data.web.pageable.default-page-size`, `@PageableDefault` | 기본 크기는 속성으로 되지만, "10·30·50이 아니면 10으로 보정"은 지원하지 않는다. 기본 정렬도 속성이 없다 |
| `JacksonConfig`(숫자 Enum 거부) | 속성 `spring.jackson.deserialization.fail-on-numbers-for-enums=true` | common은 라이브러리 모듈이라 yml을 서비스에 실어 보낼 방법이 없다. Java 설정이면 config-server가 없는 테스트에서도 똑같이 적용된다 |
| 문서 라우트 5개를 명시 | Gateway `discovery.locator.enabled=true`(서비스마다 라우트 자동 생성) | 자동 라우트는 `/user-service/internal/**`까지 외부에 연다. SA 8.3의 `/internal` 차단을 깨므로 쓰면 안 되는 내장 기능의 예 |
| user-service JWT 발급에 JJWT | Spring Security `NimbusJwtEncoder`(Gateway 검증과 같은 라이브러리) | SA 1.4 기술 스택이 JJWT를 명시했다. 바꾸려면 팀 결정이 필요 |

## C. 확인한 뒤 결정할 곳
| 위치 | 대안 | 확인할 것 |
|---|---|---|
| `CachedUserLookup`(Caffeine 직접 사용, 약 30줄) | Spring 6.1의 리액티브 `@Cacheable` + Caffeine 비동기 캐시 모드 | 어노테이션으로 줄일 수 있지만, 구독 취소 전파(10-4에서 고친 문제)와 실패 미캐시 동작이 같은지 검증해야 한다. 지금은 Ticker로 TTL·취소를 테스트하고 있어 바꿀 이득이 크지 않다 |
| `UserServiceClient`(WebClient 직접 호출) | Spring 6 선언형 HTTP 인터페이스(`@GetExchange`) | 코드 양은 비슷하다. 내부 API 호출이 늘어나면 바꿀 가치가 생긴다 |

## 덧붙임: 커버리지 측정 (JaCoCo, 저장소 변경 없이 임시 init script로 측정)
| 모듈 | 라인 | 분기 |
|---|---|---|
| common | 136/189 (72%) | 37/58 (64%) |
| gateway-service | 162/187 (87%) | 19/26 (73%) |
| user-service | 71/80 (89%) | 2/2 (100%) |
| config-server | 1/3 (33%) | - |
- 샘플 API 패키지는 제외했다.
- common의 `BaseEntity`, `ApiResponse`는 0%로 나오지만 실제로는 user-service 통합 테스트가 실행한다. JaCoCo 기본 리포트는 **모듈마다 자기 테스트만** 센다. 모듈을 넘는 커버리지를 보려면 `jacoco-report-aggregation` 플러그인으로 합쳐야 한다.
- 진짜로 비어 있는 곳: `OpenApiConfig`, `WebMvcConfig`(빈 등록 설정), gateway `AuthConfig`(WebClient 조립). 설정 코드라 단위 테스트 가치는 낮고, e2e가 실제 동작을 확인한다.
