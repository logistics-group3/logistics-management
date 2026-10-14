# 스파르타물류 (logistics-management)

Spring Cloud 기반 MSA 물류 관리 프로젝트. 설계 근거는 `docs/`의 ADR, API 설계서, ERD를 따른다.

## 구성 (ADR-01, 15)

| 모듈 | 포트 | 역할 | 스키마 |
| --- | --- | --- | --- |
| `eureka-server` | 8761 | 서비스 디스커버리 | - |
| `gateway` | 8080 | 단일 진입점, JWT 검증, 헤더 주입 (WebFlux, common 미사용) | - |
| `user-service` | 8081 | 사용자·인증·배송담당자 | `user_schema` |
| `hub-service` | 8082 | 허브·경로·재고 (최하위, 다른 서비스를 호출하지 않음) | `hub_schema` |
| `company-service` | 8083 | 업체·상품·입고 | `company_schema` |
| `order-service` | 8084 | 주문 Saga, Slack 알림 | `order_schema` |
| `delivery-service` | 8085 | 배송·구간 | `delivery_schema` |
| `common` | - | 기술 공통 라이브러리 (자동 설정) | - |

호출 방향: order → (company, hub, delivery), delivery → (hub, user), company → (hub, user), user → hub. 순환 없음.

## 기술 스택 (ADR-02)

Java 17 · Spring Boot 3.5.16 · Spring Cloud 2025.0.3 · PostgreSQL 16 · QueryDSL(OpenFeign 포크).
버전은 `gradle.properties` 한 곳에서만 바꾼다.

## 서비스 패키지 구조

```
com.sparta.logistics.{service}
├── presentation     controller, dto
├── application      유스케이스, 소유자 권한 확인
├── domain           엔티티, 도메인 규칙
├── infrastructure   persistence(JPA·QueryDSL), client(Feign)
└── config
```

4계층은 팀 합의에 따라 엄격하게 강제하지 않는다 (ADR-15).

## common이 제공하는 것 (ADR-15)

`AutoConfiguration.imports`로 등록되므로 서비스는 의존성만 추가하면 된다.

| 패키지 | 내용 |
| --- | --- |
| `entity` | `BaseEntity`(감사 6컬럼·논리 삭제), `BaseCreatedEntity`(기록형 테이블) |
| `response` | `ApiResponse`, `PageResponse`, `ErrorResponse` |
| `exception` | `ErrorCode`(API 설계서 에러 코드 전체), `BusinessException`, `GlobalExceptionHandler` |
| `security` | `UserContext`, `@CurrentUser`, `@RequireRole`, `HeaderAuditorAware` |
| `feign` | `FeignHeaderInterceptor`, `FeignErrorDecoder`, `IdempotentFeignConfig`(멱등 API 재시도 3회, 선택 적용) |
| `page` | `SearchPageRequest`(page/size/sort 규칙, 정렬 허용 목록 검증) |
| `querydsl` | `JPAQueryFactory` 빈, `QuerydslSupport`(notDeleted, 허용 목록 기반 orderBy) |

Q클래스는 각 서비스에서 생성한다. `common`에도 annotation processor를 둔 이유는 `QBaseEntity`를 서비스 Q클래스가 참조하기 때문이다.

## 로컬 실행

```bash
docker compose up -d            # PostgreSQL 16 + 서비스별 스키마·계정 생성 (docker/postgres/init)
./gradlew :eureka-server:bootRun
./gradlew :gateway:bootRun
./gradlew :user-service:bootRun # 나머지 서비스도 동일
```

환경변수로 `DB_HOST`, `DB_PASSWORD`, `JWT_SECRET`, `SLACK_WEBHOOK_URL`, `EUREKA_URL` 등을 덮어쓸 수 있다. 기본값은 로컬 개발용이다.

## 아직 구현하지 않은 것

- 도메인 엔티티·API·Saga 등 비즈니스 코드
- Gateway의 JWT 검증·권한 재확인(60초 캐시)·`X-User-*` 헤더 제거/주입 필터 (`gateway/filter`)
- 허브 17개·경로 272개 시드 데이터
