# 스텝 16. common 조건부 자동 설정

- 날짜: 2026-10-14
- 결정 기록: [ADR 0009](../../decisions/0009-common-conditional-auto-configuration.md)
- 근거: Notion "팀 합의 대조·추가 검토" 3장 "부분적으로 공통 Configuration이 적용되게끔". 방식은 KEJ·KMH·seungkeun 브랜치와 같다.
- 결과: common 테스트 34개, user-service 테스트 17개 통과. 새 테스트는 `CommonAutoConfigurationTest` 7개, `CommonAutoConfigurationIntegrationTest` 1개, `AuthControllerWebMvcTest` 1개

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `common/.../autoconfigure/Common*AutoConfiguration` 6개 | 웹, JPA Auditing, QueryDSL, Feign, Jackson, OpenAPI. 조건과 `common.<묶음>.enabled` 끄기 속성 |
| `META-INF/spring/...AutoConfiguration.imports` | 위 6개 등록 |
| `META-INF/spring/...AutoConfigureWebMvc.imports`, `...AutoConfigureDataJpa.imports` | 슬라이스 테스트(`@WebMvcTest`, `@DataJpaTest`)에도 공통 설정 포함 |
| `config/WebMvcConfig` → `config/CommonWebMvcConfigurer` | 일반 클래스로 바꾸고 순서는 `Ordered`로 지정 |
| `config/JpaAuditingConfig` → `config/HeaderAuditorAware` | `AuditorAware` 구현 클래스. `SYSTEM` 상수 유지 |
| `FeignErrorDecoder`, `FeignHeaderInterceptor` | `@Component` 제거. 자동 설정이 등록한다 |
| 서비스 5개 `*Application` | `scanBasePackages` 제거 |
| `docs/agents/coding-conventions.md` | 스캔 규칙과 끄기·바꾸기 방법 |

## 리뷰 포인트
1. **스캔과 자동 설정의 차이**: 컴포넌트 스캔은 패키지 안의 `@Component`를 조건 없이 등록한다. 자동 설정은 `@ConditionalOnClass`, `@ConditionalOnBean`, `@ConditionalOnProperty`로 "이 서비스에 필요할 때만" 등록한다. Spring Boot의 starter가 쓰는 방식과 같다.
2. **`@ConditionalOnMissingBean`과 순서**: 서비스의 사용자 설정이 먼저 처리되고 자동 설정은 나중에 처리된다. 그래서 서비스가 같은 타입의 빈을 정의하면 공통 빈이 빠진다. 반대로 서비스 코드에서 `@ConditionalOnMissingBean`을 쓰면 순서가 보장되지 않는다.
3. **JPA·QueryDSL은 `@ConditionalOnBean(EntityManagerFactory)` + `after = HibernateJpaAutoConfiguration`**: JPA 설정이 끝난 뒤에 조건을 평가해야 EntityManagerFactory 유무를 정확히 안다. DB가 없는 서비스에서도 기동이 실패하지 않는다(테스트로 확인).
4. **슬라이스 테스트**: `@WebMvcTest`는 웹 계층에 필요한 자동 설정 목록만 올린다. 스캔 방식일 때는 `@RestControllerAdvice`가 스캔으로 들어왔지만, 자동 설정으로 바꾸면 목록에 등록해야 한다. 등록 파일을 빼고 돌리면 `AuthControllerWebMvcTest`가 실패한다(검증 실패 응답에 `code`가 없음).
5. **순서를 `@Order` 대신 `Ordered`로**: `@Bean`으로 등록한 `WebMvcConfigurer`도 `Ordered` 구현이면 목록 정렬에 확실히 반영된다. Spring Data의 Pageable 처리기보다 먼저 등록돼야 공통 Pageable 규칙이 적용된다(스텝 10-1).

## 팀원에게 알릴 것
- 서비스 메인 클래스에 `scanBasePackages`를 다시 넣지 않는다. 넣어도 동작은 하지만 규칙이 두 가지가 된다.
- 공통 동작을 서비스만 다르게 하고 싶으면 먼저 같은 타입의 빈을 정의해 바꾸고, 기능 전체를 빼야 할 때만 `common.<묶음>.enabled=false`를 쓴다.
- `@DataJpaTest`를 쓸 때 예전처럼 `@Import(JpaAuditingConfig.class)`를 붙이지 않는다. 공통 Auditing과 QueryDSL이 자동으로 들어간다.
