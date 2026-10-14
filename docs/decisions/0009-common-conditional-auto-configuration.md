# 0009. common 공통 빈을 조건부 자동 설정으로 등록

- 상태: 채택
- 날짜: 2026-10-14
- 결정자: 팀 (Notion "main 통합 방안 — 팀 합의 대조·추가 검토" 3장, 구현 방식은 junhyun 선택)
- 관련: 0006(common-core 분리)을 보완한다. 0006의 모듈 경계는 그대로 둔다.

## 맥락
- 통합안은 서비스 메인 클래스가 `scanBasePackages = "com.sparta.logistics"`로 common 패키지를 스캔해 공통 빈을 등록했다. 등록에 조건이 없어 서비스가 일부만 끄거나 바꿀 방법이 없었다.
- 팀 의견: "부분적으로 공통 Configuration이 적용되게끔. 전역으로 하면 안 되는 부분이 있을 수 있음".
- KEJ, KMH, seungkeun 브랜치는 이미 `AutoConfiguration.imports` 방식이었다. seungkeun ADR-15도 같은 방향이다.

## 결정
- common의 공통 빈을 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 등록한 자동 설정 6개로 나눈다.

| 자동 설정 | 등록하는 것 | 켜지는 조건 | 끄기 |
|---|---|---|---|
| `CommonWebAutoConfiguration` | `GlobalExceptionHandler`, `CommonWebMvcConfigurer`(`@CurrentUser`, Pageable 규칙, `@RequireRole`) | 서블릿 웹 애플리케이션 | `common.web.enabled=false` |
| `CommonJpaAutoConfiguration` | `@EnableJpaAuditing`, `auditorAware`(`HeaderAuditorAware`) | `EntityManagerFactory` 빈이 있을 때 | `common.jpa-auditing.enabled=false` |
| `CommonQuerydslAutoConfiguration` | `JPAQueryFactory` | QueryDSL 클래스와 `EntityManagerFactory` 빈 | `common.querydsl.enabled=false` |
| `CommonFeignAutoConfiguration` | `FeignHeaderInterceptor`, `FeignErrorDecoder` | Feign 클래스 | `common.feign.enabled=false` |
| `CommonJacksonAutoConfiguration` | 숫자 Enum 거부 customizer | Jackson 클래스 | `common.jackson.enabled=false` |
| `CommonOpenApiAutoConfiguration` | `OpenAPI` 문서 설정 | springdoc 클래스, 서블릿 웹 | `common.openapi.enabled=false` |

- 서비스가 같은 타입의 빈을 정의하면 공통 빈은 빠진다(`@ConditionalOnMissingBean`). 예: `GlobalExceptionHandler`를 상속한 빈, `ErrorDecoder` 빈, `JPAQueryFactory` 빈, 이름이 `auditorAware`인 빈.
- 서비스 메인 클래스는 기본 `@SpringBootApplication`으로 자기 패키지만 스캔한다. common 클래스의 `@Component`를 지워 스캔으로 다시 등록되지 않게 한다.
- 슬라이스 테스트에도 공통 설정이 들어가게 `AutoConfigureWebMvc.imports`(웹, Jackson)와 `AutoConfigureDataJpa.imports`(JPA Auditing, QueryDSL)에 등록한다.
- `IdempotentRetryConfig`는 지금처럼 자동 설정에 넣지 않고 FeignClient별로 붙인다. 모든 클라이언트에 걸면 멱등하지 않은 POST까지 재시도한다.

## 검토한 대안
- 컴포넌트 스캔 유지: 끄거나 바꿀 방법이 없고, 서비스가 스캔 범위를 넓혀야 하는 규칙을 계속 기억해야 한다.
- 필수 기능만 자동 등록하고 나머지는 서비스가 `@EnableXxx`로 켜기: 서비스마다 어노테이션을 반복하고, 빠뜨리면 서비스끼리 응답 형식이나 Audit 동작이 달라진다. 팀이 정한 형식을 기본값으로 지키는 쪽을 골랐다.

## 결과
- 기본 동작은 이전과 같다. 서비스는 아무것도 하지 않아도 공통 빈을 받는다.
- JPA Auditing을 끄면 BaseEntity의 NOT NULL Audit 컬럼이 비어 저장이 실패한다. 끄는 서비스는 `@EnableJpaAuditing`을 직접 선언해야 한다. 켜 둔 채로 또 선언하면 같은 빈이 두 번 등록돼 기동에 실패한다.
- 확인: `CommonAutoConfigurationTest`(조건 7가지), user-service `CommonAutoConfigurationIntegrationTest`(실제 기동 시 빈이 한 번씩 등록), `AuthControllerWebMvcTest`(슬라이스에서 SA 에러 형식). 슬라이스 등록 파일을 빼면 `AuthControllerWebMvcTest`가 실패하는 것을 확인했다.
