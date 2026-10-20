---
title: common과 common-core 모듈은 무엇이 다른가
date: 2026-10-19
updated: 2026-10-19
asker: kmh
level: 중급
tags: [gradle, multi-module, common, webflux, mvc, gateway]
---

# common과 common-core 모듈은 무엇이 다른가

## 질문
프로젝트에 공통 모듈이 `common`과 `common-core` 두 개로 나뉘어 있다. 둘은 무엇이 다른가.

## 짧은 답
기준은 웹 스택(Spring MVC, WebFlux)에 의존하느냐다.
- `common-core`: 웹 스택과 무관한 계약만 둔다. 에러 코드, 에러·성공 응답 형식, 역할(Role), 사용자 헤더 이름. MVC 서비스와 WebFlux 기반 gateway가 함께 쓴다.
- `common`: MVC 서비스(user, hub, company, order, delivery)만 쓰는 공통 기능을 둔다. 예외 처리기, JPA Audit, 현재 사용자 주입, 역할 검사, Feign 공통 처리, 페이지·정렬, 통합 테스트 기반. `common-core`를 `api`로 포함한다.

gateway는 WebFlux라서 MVC를 끌고 오는 `common`에 의존할 수 없다. 그래도 서비스와 같은 에러 코드와 응답 형식을 써야 하므로, 둘이 같이 쓸 수 있는 부분만 `common-core`로 떼어 냈다(ADR 0006).

## 자세히

### 의존 관계
```
common-core  (spring-web, jackson-annotations만)
   ▲      ▲
   │      └── gateway-service (WebFlux)        implementation project(':common-core')
   │
common  (starter-web, data-jpa, validation, openfeign, springdoc-webmvc, querydsl)
   ▲                                            api project(':common-core')
   └── user/hub/company/order/delivery-service   implementation project(':common')
```
- 서비스는 `common`만 선언해도 `common-core`의 클래스를 쓸 수 있다. `common`이 `api`로 다시 노출하기 때문이다(`java-library` 플러그인의 `api`는 의존성을 소비자에게 전이하고, `implementation`은 전이하지 않는다).
- 두 모듈은 패키지 이름이 같다(`com.sparta.logistics.common.*`). 분리 전의 import가 그대로 동작하게 하려고 패키지를 유지했다.

### 들어 있는 것
| 모듈 | 클래스 | 웹 스택 의존 |
|---|---|---|
| common-core | `ErrorCode`, `CommonErrorCode`, `SimpleErrorCode`, `BusinessException`, `ErrorResponse`, `ApiResponse`, `Role`, `UserHeaders` | 없음. `HttpStatus`(spring-web)와 Jackson 어노테이션만 쓴다 |
| common | `GlobalExceptionHandler`(`@RestControllerAdvice`), `BaseEntity`·`HeaderAuditorAware`(JPA Audit), `@CurrentUser`·`CurrentUserArgumentResolver`, `@RequireRole`·`RequireRoleInterceptor`, `FeignHeaderInterceptor`·`FeignErrorDecoder`, `PageResponse`·`SortAllowList`, `QuerydslSupport`, 자동 설정(`autoconfigure/`), testFixtures의 `PostgresIntegrationTest` | 있음. Servlet 기반 MVC, JPA, Feign |

### 왜 WebFlux 앱은 MVC 모듈에 의존하면 안 되나
- Spring Boot는 classpath를 보고 웹 애플리케이션 종류를 정한다. `spring-webmvc`(Servlet)가 보이면 Servlet 앱으로 판단해 Tomcat을 띄우려 한다.
- Spring Cloud Gateway(WebFlux 버전)는 Netty 위의 리액티브 앱이어야 한다. MVC가 섞이면 시작 단계에서 충돌하거나 의도와 다른 서버로 뜬다.
- `spring-web`은 MVC와 WebFlux가 함께 쓰는 기반 모듈이라(`HttpStatus`, `HttpHeaders` 등) 어느 쪽에 넣어도 문제가 없다. 그래서 `common-core`는 이것만 가진다.

### 분리 전에 있던 문제
분리 전에는 gateway에 `UserHeaders`, `GatewayErrorCode`, `ApiEnvelope`를 따로 정의했다. 같은 의미의 코드가 두 곳에 있으면 한쪽만 고쳤을 때 gateway의 401 응답과 서비스의 401 응답 형식이 달라질 수 있다. 지금은 gateway의 `ErrorResponseWriter`도 `common-core`의 `ErrorCode`, `ErrorResponse`를 쓰고, 응답 형식은 `ErrorResponseContractTest` 하나가 양쪽에 함께 고정한다.

### 새 공통 코드를 어디에 둘지 고르는 법
1. gateway도 써야 하는가? 아니면 `common`이다.
2. gateway도 쓴다면, MVC·WebFlux·JPA 클래스 없이 만들 수 있는가? 그렇다면 `common-core`다.
3. 둘 다 아니면(웹 스택별 구현이 필요하면) 계약(인터페이스, 상수)만 `common-core`에 두고 구현은 `common`과 gateway에 각각 둔다. 헤더 이름은 `UserHeaders`(common-core)에 있고, 헤더를 읽는 코드는 `UserContextExtractor`(common), 넣는 코드는 `UserHeaderInjectionFilter`(gateway)인 것이 이 예다.

### 더 깊이
- 검토한 대안은 `common`을 `common-core`와 `common-web`으로 이름까지 바꾸는 것이었다. 서비스 5개의 의존성 선언과 문서를 모두 바꿔야 해서 이름은 유지했다(`docs/decisions/0006-common-core-module.md`).
- `common`의 빈은 컴포넌트 스캔이 아니라 자동 설정으로 등록한다(`docs/decisions/0009-common-conditional-auto-configuration.md`). 서비스 패키지가 `com.sparta.logistics.{서비스}`라서 `common` 패키지는 스캔 범위 밖이기도 하다.

## 면접 포인트
- "MSA에서 공통 모듈을 어떻게 관리했나": 공통 모듈이 비대해지면 모든 서비스가 불필요한 의존성을 떠안는다. 의존 대상(웹 스택)을 기준으로 계약과 구현을 나눠, 리액티브 gateway와 MVC 서비스가 같은 응답 계약을 공유하게 했다.
- "Gradle `api`와 `implementation` 차이": `api`는 소비 모듈의 컴파일 classpath까지 전이되고, `implementation`은 전이되지 않아 재컴파일 범위와 의존성 노출을 줄인다.
- 꼬리 질문 대비: 공통 모듈 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰와 계약 테스트(`ErrorResponseContractTest`)로 막는다.
