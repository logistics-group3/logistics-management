# CLAUDE.md — 스파르타 물류 MSA 프로젝트

> 원본 설계 문서: `Docs/reference/SA_Document.md` (최종 근거). 주제별 정리본(개요·ERD·API·인프라·시스템 구조)은 `Docs/` 참고.
> 이 파일은 **모든 서비스에 적용되는 대전제와 필수 규칙**만 담는다. 서비스별 세부 규칙은 각 모듈의 `CLAUDE.md`에 있다.
> 규칙이 애매하거나 정해지지 않은 사항은 임의로 정하지 말고 사용자에게 확인한다.

## 0. 개요
- 주제: 전국 17개 허브를 운영하는 B2B 물류 회사의 주문·재고·배송 MSA
- 기간: 2026-10-12 ~ 2026-10-22 (발표 10/22 10:00), 3조 6인 팀 프로젝트
- 프론트엔드 없음. REST API만 제공 (Swagger UI / Postman으로 확인)

## ⚠️ 협업 규칙: 규칙 문서 구조 (반드시 준수)
| 위치 | 내용 |
| --- | --- |
| 루트 `CLAUDE.md` (이 파일) | 대전제·필수 규칙, 서비스 간 공통 계약 |
| `{모듈}/CLAUDE.md` | 그 모듈의 **세부 규칙**(도메인 규칙·상태 전이·컬럼 길이 등). 첫 줄 `@agent.md`로 agent.md를 함께 불러온다 |
| `{모듈}/agent.md` | 서비스 정보·제공/의존 API 계약·구현 현황·**결정 필요 사항과 충돌을 주고받는 커뮤니케이션 로그** |
| `.claude/skills/` | 작업 **절차**(학습 `learn-*`, 구현 `impl-*`). 규칙 본문은 담지 않고 위 문서를 가리킨다. 이 프로젝트는 학습이 주 목적이므로 기능 구현은 `impl-service` 절차(계층별 설명 + 구현 노트)를 따른다 |

대상 모듈: `common/`, `gateway-service/`, `user-service/`, `hub-service/`, `company-service/`, `order-service/`, `delivery-service/`

1. **모듈 코드를 수정하기 전에 그 모듈의 `CLAUDE.md`와 `agent.md`를 먼저 읽는다.** 로그에 `OPEN` 항목이 작업과 관련 있으면 임의로 결정하지 말고 사용자(담당자)에게 확인한다.
2. **다른 서비스의 코드는 수정하지 않는다.** 다른 서비스의 내부 API(I-xx) 계약 변경·추가가 필요하면 **요청 받는 서비스의 `agent.md`** 로그에 `OPEN` 항목을 추가하고 멈춘다.
3. 내부 API를 **제공하는** 쪽이 계약(경로·요청·응답 필드·상태 코드)을 바꾸려면, 자기 `agent.md`에 기록하고 호출자 서비스 담당자와 합의(`DECIDED`) 후 변경한다.
4. `common` 모듈과 루트 공유 파일(`build.gradle`, `settings.gradle`, `docker-compose.yml`, `Dockerfile`, `.env.example`, `infra/`, 이 `CLAUDE.md`)은 모든 서비스에 영향을 준다 → 변경 전 `common/agent.md`에 기록한다.
5. 작업 중 내린 결정·발견한 충돌은 해당 `agent.md` 로그에 남긴다. 결정이 규칙을 바꾸면 해당 규칙 문서(SA 문서, 루트·모듈 `CLAUDE.md`)도 함께 수정한다.
6. 로그 작성: 맨 아래에만 추가(Git 충돌 최소화), ID는 `{서비스}-{번호}`(예: `HUB-003`), 상태는 `OPEN`(논의 필요) → `DECIDED`(결정 내용 기록) → `DONE`(코드 반영).
7. 우선순위: `Docs/reference/SA_Document.md` > 루트 `CLAUDE.md` > 모듈 `CLAUDE.md` / `agent.md`. 둘이 어긋나면 작업을 멈추고 로그에 `OPEN`으로 기록한다.
8. 서비스 세부 규칙은 해당 모듈 `CLAUDE.md` 한 곳에만 둔다 (루트·agent.md에 중복 기재 금지). 여러 서비스가 함께 지켜야 하는 계약만 루트에 둔다.

### 학습·구현 skill 제안 (AI 행동 규칙)
아래 순간에는 답변 끝에 **한 줄로 skill을 제안**하고, 지시자가 동의하면 실행한다. 지시자 동의 없이 기록 파일을 쓰지 않는다.

| 이런 순간 | 제안 |
| --- | --- |
| 기능 구현·버그 수정을 마치고 커밋했을 때, "오늘은 여기까지" 같은 마무리 발언 | "오늘 내용을 `/learn-note`로 정리할까요?" |
| 오래 막혔던 문제를 해결했을 때 | "트러블슈팅으로 남겨 둘까요?" (`learn-note`) |
| 지시자가 직접 짠 코드를 커밋하려 할 때 | "커밋 전에 `/learn-review`로 점검할까요?" |
| 기능 구현을 맡았는데 `impl-service` 절차 밖에서 진행하려 할 때 | "`impl-service` 절차(계층별 설명)로 진행할까요?" |
| 설명을 듣고도 헷갈려할 때 | "`/learn-explain`으로 우리 코드와 연결해서 볼까요?" |

- 한 시점에 한 번만 제안한다. 거절한 제안은 같은 대화에서 반복하지 않는다.
- 지시자가 "제안 그만"이라고 하면 그 대화에서는 모든 skill 제안을 멈춘다.
- 이미 해당 skill을 실행 중이거나 지시자가 직접 호출한 경우에는 제안하지 않는다.

## 0-1. 진행 단계
- **공통 인프라 구축 (완료, 2026-10-13)** — 원본: `Docs/reference/공통 인프라 개발 범위.md`
  - 범위 원칙: 모든 서비스가 같이 쓰고, 나중에 바꾸면 전부 고쳐야 하는 것까지만. 도메인 로직은 넣지 않는다.
  - 구현: 프로젝트 뼈대(서비스 5개 빈 모듈), eureka-server, gateway-service, common, 인프라(compose·Dockerfile·`.env.example`), user-service 인증(U-01, U-02, I-01) + 검증용 U-03, U-08
  - 완료 기준 6가지(compose 한 번으로 기동, 가입→로그인→토큰, `@CurrentUser` 수신, 401/403, `/internal/**` 차단, 위조 `X-User-Role` 무시) 모두 로컬 검증 완료. 빈 볼륨·빈 빌드 캐시에서 첫 빌드 약 4분
  - 브랜치: `{이름}/base-setting`, 10/14(수) 오전 리뷰 후 하나 채택
- **서비스 구현 (10/14~)**: 도메인 Entity·CRUD·검색, 서비스별 FeignClient, Saga·보상, 허브 시드, Slack, U-04~U-07은 각 서비스 담당자. 각 모듈 `agent.md` §5 구현 현황 참고

## 1. 기술 스택 (변경 금지)
- Java 17, Spring Boot 3.x (+ 호환되는 Spring Cloud 릴리스 트레인), Gradle 멀티 모듈
- Spring Cloud Gateway / Eureka / OpenFeign + LoadBalancer
- Spring Data JPA + QueryDSL, PostgreSQL 16
- Spring Security, JJWT, BCrypt, Bean Validation, springdoc-openapi
- Docker / Docker Compose, Slack Incoming Webhook
- 선택: Resilience4j, Micrometer Tracing + Zipkin, Redis, Kafka(고도화 단계)

## 2. 서비스 경계
| 서비스 | 포트 | 책임 | 스키마 |
| --- | --- | --- | --- |
| eureka-server | 8761 | 서비스 등록·조회 | - |
| gateway-service | 8080 | 단일 진입점, JWT 검증, 라우팅, `/internal/**` 차단 | - |
| user-service | 8081 | 회원가입·로그인(JWT 발급), 사용자·권한·배송담당자 관리 | user_schema |
| hub-service | 8082 | 허브·허브 경로 조회, **재고 소유**(입고·할당·복원) | hub_schema |
| company-service | 8083 | 업체 CRUD, **상품 소유**(업체 하위 리소스) | company_schema |
| order-service | 8084 | 주문 생성(Saga 오케스트레이터)·취소·삭제, Slack 알림 | order_schema |
| delivery-service | 8085 | 배송·배송경로 생성, 담당자 배정, 상태 관리 | delivery_schema |

config server(포트 미정)·front 3000은 **추후 도입**.

## 3. 핵심 설계 규칙 (전 서비스 공통)
1. **인증·인가는 Gateway에서만.** Gateway가 JWT 검증 후 `X-User-Id`, `X-Username`, `X-User-Role`, `X-Delivery-Manager` 헤더를 주입한다 (클라이언트가 보낸 동명 헤더는 제거). 하위 서비스는 이 헤더를 신뢰하고 **소유자 검증 같은 비즈니스 인가만** 한다.
2. **권한은 `MASTER` / `USER` 2단계.** 배송담당자는 별도 권한이 아니라 USER의 `is_delivery_manager = true` + 소속 `hub_id` 속성.
3. **서비스 간 FK 금지.** 다른 서비스 데이터는 UUID 논리 참조만 하고, 존재 여부는 Feign으로 검증. 다른 스키마 직접 조회·조인 금지.
4. **모든 PK는 UUID.** 테이블명은 `p_` 접두사.
5. **BaseEntity 공통 Audit**: `created_at/by`, `updated_at/by`, `deleted_at/by`.
6. **삭제는 전부 논리 삭제**(`deleted_at`, `deleted_by`). 논리 삭제 데이터는 조회되지 않는다. 유니크 제약은 `WHERE deleted_at IS NULL` 부분 인덱스로.
7. **서비스 간 도메인 불변식** (여러 서비스가 함께 지킨다, 상세는 각 모듈):
   - **주문 1건 = 배송 1건**, 주문은 한 번에 한 품목.
   - **배송 생성·삭제는 내부 API로만** 제공 (주문 없는 배송, 배송 없는 주문 방지).
   - **상품은 공급업체 소속 허브에만 존재**하고 출고 허브는 항상 공급업체 허브. 재고 부족 시 주문 실패(409 `STOCK_INSUFFICIENT`), 아무것도 변경하지 않는다.
   - 업체 간 배송 구조: 공급업체 → 공급업체 허브 → (허브 이동, 같은 허브면 생략) → 요청업체 허브 → 요청업체.

## 4. 재고 규칙 → `hub-service/CLAUDE.md`
- 공통 전제는 §3-7. 재고 행 구조, `p_stock_allocations`, I-05/I-06 처리 순서와 락 순서는 hub-service에 있다.

## 5. 주문 생성 Saga → `order-service/CLAUDE.md`
- **오케스트레이션** (order-service가 주도). 실패 시 내부적으로 역순 보상, 클라이언트에는 최종 에러 하나만 응답.
- **서비스 간 계약: 재고 할당·복원(I-05, I-06, hub), 배송 생성·삭제(I-09, I-13, delivery) API는 모두 orderId 기준 멱등이어야 한다** (같은 orderId 재요청 시 기존 결과 반환 / 대상 없으면 no-op). 단계별 실패 처리 표는 order-service에 있다.

## 6. API 공통 규약
- 외부 경로는 `/api/v1/**`, 내부 Feign 전용 경로는 `/internal/**` (Gateway에서 라우팅하지 않음)
- 회원가입·로그인 외 모든 API는 `Authorization: Bearer {accessToken}` 필요
- 응답 형식: 성공 `{status, message, data}` / 에러 `{status, code, message, path, timestamp}`
- 페이징: `page`(0부터), `size`(10/30/50만 허용), `sort`(기본 `createdAt,DESC`)
- 에러 코드: `VALIDATION_ERROR`, `INVALID_REQUEST`, `MANAGER_HUB_MISMATCH`(400) / `UNAUTHORIZED`, `TOKEN_ROLE_MISMATCH`, `LOGIN_FAILED`(401) / `FORBIDDEN`(403) / `{리소스}_NOT_FOUND`, `DELIVERY_MANAGER_NOT_FOUND`(404) / `DUPLICATE_USERNAME`, `STOCK_INSUFFICIENT`, `INVALID_STATUS_TRANSITION`, `HUB_CHANGE_NOT_ALLOWED`, `ORDER_NOT_DELETABLE`(409) / `SERVICE_UNAVAILABLE`(503)
- API 목록(U/H/C/P/O/D-xx)과 내부 API(I-01~I-14)의 상세 명세는 `Docs/reference/SA_Document.md` §2를 따른다. 서비스별 제공·의존 API 요약은 각 `agent.md` §2·§3.

## 7. 상태 전이 → `order-service/CLAUDE.md`(주문), `delivery-service/CLAUDE.md`(배송)
- **상태 전이 검증은 Entity 메서드에 둔다.** 위반 시 409 `INVALID_STATUS_TRANSITION`.

## 8. 코드 구조 규칙
- 이 디렉터리가 Gradle 루트(`rootProject.name = 'sparta-logistics'`): `common`, `eureka-server`, `gateway-service`, `user-service`, `hub-service`, `company-service`, `order-service`, `delivery-service`, `infra/postgres/init.sh`(SA의 init.sql을 환경 변수 비밀번호 사용을 위해 셸로 작성), `Docs/`(설계 문서, 원본은 `Docs/reference/`), `docker-compose.yml`, `Dockerfile`(전 모듈 공용 멀티 스테이지, `MODULE` 인자), `script/run.sh`
- `common` 모듈: `BaseEntity`, `ApiResponse`, `PageResponse`, `GlobalExceptionHandler`, `ErrorCode`, `UserContext`/`@CurrentUser`, `FeignHeaderInterceptor` (상세: `common/agent.md` §2)
- 패키지: `com.sparta.logistics.{service}` 하위에 Layered Architecture
  - `presentation/` controller, request/response DTO
  - `application/` service(UseCase), command/result DTO, 트랜잭션 경계, Saga
  - `domain/` entity, enum, VO, repository **인터페이스**
  - `infrastructure/` JPA·QueryDSL 구현, FeignClient, Slack client, config
- 의존 방향: presentation → application → domain, infrastructure는 domain 인터페이스를 구현(의존성 역전)
- **Entity는 Controller 밖으로 노출 금지**, 계층별 DTO 분리

### 8-1. 구현 컨벤션 (반드시 준수)
- 버전: Spring Boot 3.5.16, Spring Cloud 2025.0.3, Gradle 8.14.3(wrapper), QueryDSL 5.1.0(jakarta), JJWT 0.12.6, springdoc 2.8.13. 버전은 루트 `build.gradle`의 `ext`에서만 관리.
- 비즈니스 서비스는 **`common` 하나만 의존**하면 Web·Validation·JPA·OpenFeign·QueryDSL·Swagger·Eureka Client·PostgreSQL이 함께 들어온다 (루트 `build.gradle`에서 주입). 공통 설정은 `CommonAutoConfiguration`이 자동 등록되므로 `scanBasePackages` 지정 금지.
- `gateway-service`는 WebFlux이므로 `common`을 의존하지 않는다 (서블릿 의존성 충돌 방지).
- **응답**: 외부 API(`/api/v1/**`)는 `ApiResponse.ok/created(...)`로 감싸고, 목록은 `PageResponse.from(page, mapper)`. **내부 API(`/internal/**`)는 DTO를 래퍼 없이 그대로 반환**(Feign 역직렬화 단순화).
- **페이징**: 서비스 계층에서 `Pageables.normalize(pageable)` 호출 (size 10/30/50 외는 10, 정렬 없으면 `createdAt DESC`).
- **예외**: 서비스별 에러 코드는 `ErrorCode`를 구현한 enum(예: `UserErrorCode.USER_NOT_FOUND`)으로 정의하고 `throw new BusinessException(code)`. 공통 코드는 `CommonErrorCode`(VALIDATION_ERROR, INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN, INVALID_STATUS_TRANSITION, SERVICE_UNAVAILABLE 등).
- **Feign 에러 전파**: 하위 서비스 4xx는 코드·상태 그대로 전달(예: hub의 409 `STOCK_INSUFFICIENT` → order 응답도 409), 5xx·연결 실패·타임아웃은 503 `SERVICE_UNAVAILABLE` (`FeignErrorDecoder`, `GlobalExceptionHandler`). X-User-* 헤더는 `FeignHeaderInterceptor`가 자동 전파. Feign 재시도는 기본 꺼짐 → 멱등 API 클라이언트에만 개별 설정.
- **사용자 식별**: 컨트롤러 파라미터 `@CurrentUser UserContext user` (`isMaster()`, `isSelf(id)`). 헤더 없으면 401.
- **MASTER 전용 API**: 컨트롤러 메서드(또는 클래스)에 `@MasterOnly` → MASTER가 아니면 403 `FORBIDDEN` (common 인터셉터). 권한 규칙은 Gateway가 아니라 API 선언부에 둔다. 소유자 검증(본인·업체 관리자 등)은 서비스 계층에서 직접 한다.
- **부분 유니크 인덱스**: `src/main/resources/db/indexes.sql`에 `CREATE UNIQUE INDEX IF NOT EXISTS ... WHERE deleted_at IS NULL`로 작성. Hibernate DDL 이후 실행되도록 `spring.jpa.defer-datasource-initialization: true`, `spring.sql.init.mode: always`, `spring.sql.init.data-locations: classpath:db/indexes.sql` 설정 (user-service 참고).
- **엔티티**: `extends BaseEntity`, `@Table(name = "p_...")`, `@SQLRestriction("deleted_at IS NULL")`(논리 삭제 행 제외, 엔티티마다 직접 선언), PK는 `@GeneratedValue(strategy = GenerationType.UUID)` — **단, `Order`는 Saga를 위해 서비스에서 선발급**. 삭제는 `softDelete(userId)`.
- **스키마 생성**: 현재 `ddl-auto: update` (개발용). 부분 유니크 인덱스 등 JPA로 만들 수 없는 제약은 별도 SQL로 추가.
- **JWT 계약** (user-service 발급 ↔ gateway 검증): HS256, 키는 `JWT_SECRET`(Base64, 32바이트 이상), `sub` = userId(UUID), claim `username`, `role`(MASTER/USER), `iat`, `exp`, 만료 `JWT_EXPIRATION_MS`(기본 60분). JWT 키 관리 방식은 팀 논의 후 변경될 수 있음 (USER-001).
- **권한 변경 반영 지연**: Gateway가 I-01 결과를 userId 기준 **60초 캐시**하므로 권한 변경·탈퇴는 최대 60초 늦게 반영된다 (상세: `gateway-service/CLAUDE.md`).

### 8-2. 실행 방법 (요약 — 상세는 루트 `README.md` 빠른 실행, `Docs/04-인프라-구성도.md` 실행 방법·자주 겪는 문제)
- 최초 1회 `cp .env.example .env` 후 `JWT_SECRET` 채우기 → 전체 실행 `docker compose up -d --build` (컨테이너 안에서 빌드, 호스트 JDK 불필요)
- IDE에서 일부 서비스만 실행: `docker compose up -d postgres eureka-server` 후 IDE에서 실행 (yml이 루트 `.env`를 자동으로 읽음)
- 확인: Swagger `http://localhost:8080/swagger-ui.html`, 초기 MASTER는 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`. 새 인스턴스 반영까지 수십 초 걸릴 수 있다
- Windows Git Bash curl로 한글 JSON을 보낼 때는 UTF-8 파일 + `--data-binary @file.json` (`-d '...'`는 깨져서 400)

## 9. 인프라 규칙
- PostgreSQL 컨테이너 1개 + **서비스별 스키마 + 전용 계정**(자기 스키마 권한만). `currentSchema` 또는 `hibernate.default_schema`로 지정.
- 외부 공개 포트는 Gateway 8080, Eureka 8761뿐 (postgres 5432는 개발용 선택). 비즈니스 서비스는 호스트 포트 고정 공개 금지 → `--scale` 가능해야 함.
- Eureka `instance-id`에 랜덤 값 부여, Gateway `lb://` / `@FeignClient(name=...)`로 호출. 서비스는 Stateless.
- Feign 타임아웃 (Feign 대상은 모두 내부 서비스, **바깥 호출 타임아웃 > 안쪽 호출 타임아웃** 유지):
  | 대상 | connect | read |
  | --- | --- | --- |
  | 기본값(조회) | 1초 | 3초 |
  | I-05 재고 할당 (DB `lock_timeout` 3초) | 1초 | 5초 |
  | I-09 배송 생성 (order → delivery) | 1초 | 5초 |
  | I-03 경로 조회 (delivery → hub, I-09 내부) | 1초 | 2초 |
  | Slack Webhook (외부, 비동기) | 2초 | 3초 |
  | Gateway 응답 타임아웃 | - | 30초 |
- `ErrorDecoder`로 도메인 예외 또는 503 `SERVICE_UNAVAILABLE` 변환. 재시도는 **GET과 orderId 멱등 API만, 최대 3회 시도, 100ms부터 지수 백오프**.
- Actuator `/actuator/health`, postgres healthcheck 후 서비스 기동.
- **JWT secret, DB 비밀번호, Slack Webhook URL은 `.env` → 환경 변수로 주입, Git 커밋 금지.**
- Swagger: 각 서비스 springdoc 문서를 Gateway Swagger UI에서 서비스별로 조회.

## 10. 결정 이력 / 미결정 사항

### 확정 (상세는 괄호 안 위치)
- 상품은 공급업체 소속 허브에만 존재 (회의 결정) → 다중 허브 할당·"가까운 허브 순" 로직 폐기 (§3-7, hub)
- `p_hub_stock_histories` 확장 기능으로 제외, 대체로 `p_stock_allocations` 도입 / H-05 허브 재고 검색 범위 제외 (hub)
- I-02 `/internal/hubs/{hubId}` 호출자에 user-service 추가 (user, hub)
- I-03은 단일 경로 조회(`departureHubId`, `arrivalHubId`), I-05 요청은 `orderId, productId, quantity` (hub)
- 배송담당자: MASTER 수동 배정 + `isDeliveryManager` 기준, D-04·D-06 모두 PATCH, 자동 배정 없음 (delivery)
- `p_orders.total_price` 저장 / Saga 오케스트레이션 (§5, order)
- Feign 타임아웃 (§9) / 컬럼 length (§11 + 각 모듈)
- 업체 간 배송: 경로 테이블 유지(0~1행), 배송 컬럼 추가, 상태 전이, 조회 권한 (delivery). I-09 요청에 공급·요청업체 ID 추가, I-14 `GET /internal/companies?managerId=` 신설 (company → delivery)
- 규칙 문서 구조: 루트 `CLAUDE.md`(대전제) + 모듈 `CLAUDE.md`(세부 규칙) + `agent.md`(계약·로그) (COMMON-005)

### 미결정 (구현 전 사용자 확인 필요)
- 전체 공통 사항 없음. 서비스별 미결정 사항은 각 `agent.md` 로그의 `OPEN` 항목.

## 11. 컬럼 길이 기준 (임의 지정, 필요 시 조정)
- 공통: enum 성격 컬럼(`role`, `status`, `affiliation_type` 등) `varchar(20)`, 주소 `varchar(255)`, Audit `created_by`/`updated_by`/`deleted_by`는 `uuid`
- 테이블별 길이는 해당 테이블을 소유한 모듈의 `CLAUDE.md`에 있다.
