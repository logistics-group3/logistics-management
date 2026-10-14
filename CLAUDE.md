# CLAUDE.md — 스파르타 물류 MSA 프로젝트

> 원본 설계 문서: `Docs/reference/SA문서.md` (최종 근거). 이 파일은 구현 시 반드시 지켜야 할 규칙의 요약이다. 주제별 정리본(개요·ERD·API·인프라·시스템 구조)은 `Docs/` 참고.
> 규칙이 애매하거나 아래 "미결정 사항"에 해당하면 임의로 정하지 말고 사용자에게 확인한다.

## 0. 개요
- 주제: 전국 17개 허브를 운영하는 B2B 물류 회사의 주문·재고·배송 MSA
- 기간: 2026-10-12 ~ 2026-10-22 (발표 10/22 10:00), 3조 6인 팀 프로젝트
- 프론트엔드 없음. REST API만 제공 (Swagger UI / Postman으로 확인)

## ⚠️ 협업 규칙: 서비스별 `agent.md` (반드시 준수)
각 모듈 최상위에 `agent.md`가 있다: `common/`, `gateway-service/`, `user-service/`, `hub-service/`, `company-service/`, `order-service/`, `delivery-service/`.
이 파일은 서비스 정보·제공/의존 API·고유 규칙·**결정 필요 사항과 충돌을 주고받는 커뮤니케이션 로그**다.

1. **모듈 코드를 수정하기 전에 그 모듈의 `agent.md`를 먼저 읽는다.** 로그에 `OPEN` 항목이 작업과 관련 있으면 임의로 결정하지 말고 사용자(담당자)에게 확인한다.
2. **다른 서비스의 코드는 수정하지 않는다.** 다른 서비스의 내부 API(I-xx) 계약 변경·추가가 필요하면 **요청 받는 서비스의 `agent.md`** 로그에 `OPEN` 항목을 추가하고 멈춘다.
3. 내부 API를 **제공하는** 쪽이 계약(경로·요청·응답 필드·상태 코드)을 바꾸려면, 자기 `agent.md`에 기록하고 호출자 서비스 담당자와 합의(`DECIDED`) 후 변경한다.
4. `common` 모듈과 루트 공유 파일(`build.gradle`, `settings.gradle`, `docker-compose.yml`, `Dockerfile`, `.env.example`, `infra/`)은 모든 서비스에 영향을 준다 → 변경 전 `common/agent.md`에 기록한다.
5. 작업 중 내린 결정·발견한 충돌은 해당 `agent.md` 로그에 남긴다. 결정이 SA 문서나 이 파일의 규칙을 바꾸면 그 문서도 함께 수정한다.
6. 로그 작성: 맨 아래에만 추가(Git 충돌 최소화), ID는 `{서비스}-{번호}`(예: `HUB-003`), 상태는 `OPEN`(논의 필요) → `DECIDED`(결정 내용 기록) → `DONE`(코드 반영).
7. 우선순위: `Docs/reference/SA문서.md` / 이 `CLAUDE.md`(전체 규칙) > 각 `agent.md`(서비스 규칙). 둘이 어긋나면 작업을 멈추고 로그에 `OPEN`으로 기록한다.

## 0-1. 현재 단계: 공통 인프라 구축 (원본: `Docs/reference/공통 인프라 개발 범위.md`)
- **범위 원칙: 모든 서비스가 같이 쓰고, 나중에 바꾸면 전부 고쳐야 하는 것까지만 넣는다. 도메인 로직은 넣지 않는다.**
- 일정: 10/13(화) 오후 각자 구현 → 10/14(수) 오전 리뷰 후 하나 채택. 브랜치: `{이름}/base-setting`
- 포함: 프로젝트 뼈대(서비스 5개는 빈 모듈), eureka-server, gateway-service(라우팅·JWT 검증·X-User-* 재주입·`/internal/**` 차단·권한 재확인 **60초 캐시**), common, 인프라(compose·Dockerfile·`.env.example`), user-service **인증 부분만**(U-01 회원가입, U-02 로그인·JWT 발급, I-01 내부 사용자 조회) + 검증용 U-03(내 정보), U-08(비활성화, MASTER 전용)
- **제외 (10/14부터 서비스 담당자)**: 도메인 Entity·CRUD·검색, 서비스별 FeignClient, Saga·보상 트랜잭션, 허브 초기 데이터, Slack 연동, 나머지 사용자 관리 API(U-04~U-07)
- 완료 기준:
  1. `docker compose up` 한 번으로 전체 기동 + Eureka 등록 (멀티 스테이지 Dockerfile로 컨테이너 안에서 빌드)
  2. 회원가입 → 로그인 → 토큰 발급
  3. 토큰으로 Gateway 경유 샘플 API(U-03) 호출 시 `@CurrentUser`로 사용자 정보 수신
  4. 토큰 없음·위조 → 401, 일반 사용자가 MASTER 전용 API(U-08) 호출 → 403
  5. 외부에서 `/internal/**` 호출 차단
  6. 클라이언트가 보낸 `X-User-Role: MASTER` 헤더 무시
- 진행 상태: 위 6가지 모두 로컬에서 검증 완료 (2026-10-13, 빈 볼륨·빈 빌드 캐시에서 `docker compose up -d --build` 한 번으로 기동, 첫 빌드 약 4분)
- 포트: eureka 8761, gateway 8080, user 8081, hub 8082, company 8083, order 8084, delivery 8085. config server(포트 미정)·front 3000은 **추후 도입**

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

## 3. 핵심 설계 규칙
1. **인증·인가는 Gateway에서만.** Gateway가 JWT 검증 후 `X-User-Id`, `X-Username`, `X-User-Role`, `X-Delivery-Manager` 헤더를 주입한다 (클라이언트가 보낸 동명 헤더는 제거). 하위 서비스는 이 헤더를 신뢰하고 **소유자 검증 같은 비즈니스 인가만** 한다.
2. **권한은 `MASTER` / `USER` 2단계.** 배송담당자는 별도 권한이 아니라 USER의 `is_delivery_manager = true` + 소속 `hub_id` 속성.
3. **서비스 간 FK 금지.** 다른 서비스 데이터는 UUID 논리 참조만 하고, 존재 여부는 Feign으로 검증. 다른 스키마 직접 조회·조인 금지.
4. **모든 PK는 UUID.** 테이블명은 `p_` 접두사.
5. **BaseEntity 공통 Audit**: `created_at/by`, `updated_at/by`, `deleted_at/by`.
6. **삭제는 전부 논리 삭제**(`deleted_at`, `deleted_by`). 논리 삭제 데이터는 조회되지 않는다. 유니크 제약은 `WHERE deleted_at IS NULL` 부분 인덱스로.
7. **주문은 한 번에 한 품목**, 요청(수령)업체 관리자 본인 또는 MASTER만 그 업체 명의로 주문 가능.
8. **주문 1건 = 배송 1건.**
9. **배송 생성·삭제는 내부 API로만** 제공 (주문 없는 배송, 배송 없는 주문 방지).
10. **배송담당자 배정**: MASTER가 `isDeliveryManager = true`인 사용자 중에서 **수동 배정**(D-04, D-06 모두 PATCH, 자동 배정·배정용 내부 API 없음). 출발지 허브 소속일 때만 가능 — 허브 이동 구간 → 공급업체 허브, 업체 배송 → 목적지(요청업체) 허브. 담당자 없음 404 `DELIVERY_MANAGER_NOT_FOUND`, 허브 불일치 400 `MANAGER_HUB_MISMATCH`.
11. 상품이 등록된 업체의 관리 허브 변경 금지 → 409 `HUB_CHANGE_NOT_ALLOWED`.
12. 상품 생성 시 업체 소속 허브에 재고 0개 레코드 생성(I-04).
13. **주문 금액 `total_price`** 는 주문 생성 시 서버가 `unit_price × quantity`로 계산해 저장 (클라이언트 입력 금지, `Math.multiplyExact` 오버플로 시 400 `INVALID_REQUEST`). 수량은 주문 후 변경 불가.

## 4. 재고 규칙 (hub-service)
- **상품은 공급업체 소속 허브에만 존재**한다 (상품당 재고 행 1개, `p_hub_stocks.product_id` UNIQUE). 다른 허브에는 재고가 없다.
- 출고 허브는 항상 공급업체 허브. 흐름: 공급업체 허브 재고 차감 → 공급업체 허브 → 요청업체 허브 이동(P2P, 같은 허브면 생략) → 요청업체로 배송.
- 재고 부족 시 **주문 실패(409 `STOCK_INSUFFICIENT`), 아무것도 변경하지 않는다.** 재고 수량은 0 이상.
- H-06 입고는 상품 소속 허브로만 가능 (다르면 400 `INVALID_REQUEST`). H-05 허브 재고 검색은 **범위 제외**.
- `p_hub_stock_histories`(재고 변경 이력)는 **확장 기능으로 제외**. 할당·복원의 멱등성과 복원 근거는 **`p_stock_allocations`** 가 담당한다.
  - 컬럼: `stock_allocation_id` PK, `order_id` UNIQUE, `hub_stock_id` FK, `quantity`(≥1), `status`(`ALLOCATED`/`RESTORED`)
  - **I-05 할당**: ① 상품 재고 행을 `FOR UPDATE`로 잠금 → ② `order_id`로 기존 할당 조회, 있으면 그대로 반환 → ③ 없으면 수량 검사(부족 시 409) 후 차감 + `ALLOCATED` 저장
  - **I-06 복원**: ① 락 없이 `order_id`로 할당 조회, 없으면 0 반환 → ② 해당 재고 행 `FOR UPDATE` → ③ `ALLOCATED`면 복원 후 `RESTORED`, 이미 복원됐으면 0
  - 락 순서는 항상 "재고 행 → 할당 내역". 순서를 바꾸면 교착 위험.

## 5. 주문 생성 Saga (order-service 오케스트레이션, 확정)
- 코레오그래피가 아닌 **오케스트레이션**: 동기 Feign 중심 스택, Kafka는 고도화 단계, 주문 → 재고 → 배송이 직선 흐름이므로.
- 내부적으로는 실패한 단계까지 **역순으로 하나씩 보상**하고, 클라이언트에는 **최종 에러 하나만** 응답.

순서: `orderId 선발급` → 업체·상품 조회(I-07, I-08) → 요청업체 관리자 본인 확인 → 재고 할당(I-05) → 배송 생성(I-09) → 주문 저장 → 커밋 후 Slack 비동기 발송

| 실패 지점 | 처리 | 응답 |
| --- | --- | --- |
| 업체·상품 조회 | 즉시 실패 | 404 / 503 |
| 재고 할당: 재고 부족 | 즉시 실패 | 409 |
| 재고 할당: 타임아웃 | 재고 복원(I-06) | 503 |
| 배송 생성 실패/타임아웃 | 배송 삭제(I-13) → 재고 복원(I-06) | 503 |
| 주문 저장 실패 | 로컬 롤백 → 배송 삭제(I-13) → 재고 복원(I-06) | 500 |
| Slack 실패 | 주문 유지, 에러 로그만 (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) | 201 |
| 보상 실패 | 최대 3회 재시도 후 orderId 포함 ERROR 로그 | - |

- **재고 할당·복원, 배송 생성·삭제 API는 모두 orderId 기준 멱등**이어야 한다 (같은 orderId 재요청 시 기존 결과 반환 / 대상 없으면 no-op).

## 6. API 공통 규약
- 외부 경로는 `/api/v1/**`, 내부 Feign 전용 경로는 `/internal/**` (Gateway에서 라우팅하지 않음)
- 회원가입·로그인 외 모든 API는 `Authorization: Bearer {accessToken}` 필요
- 응답 형식: 성공 `{status, message, data}` / 에러 `{status, code, message, path, timestamp}`
- 페이징: `page`(0부터), `size`(10/30/50만 허용), `sort`(기본 `createdAt,DESC`)
- 에러 코드: `VALIDATION_ERROR`, `INVALID_REQUEST`, `MANAGER_HUB_MISMATCH`(400) / `UNAUTHORIZED`, `TOKEN_ROLE_MISMATCH`, `LOGIN_FAILED`(401) / `FORBIDDEN`(403) / `{리소스}_NOT_FOUND`, `DELIVERY_MANAGER_NOT_FOUND`(404) / `DUPLICATE_USERNAME`, `STOCK_INSUFFICIENT`, `INVALID_STATUS_TRANSITION`, `HUB_CHANGE_NOT_ALLOWED`, `ORDER_NOT_DELETABLE`(409) / `SERVICE_UNAVAILABLE`(503)
- API 목록(U/H/C/P/O/D-xx)과 내부 API(I-01~I-14)의 상세 명세는 `Docs/reference/SA문서.md` §2를 따른다.

## 7. 상태 전이
- 주문: `ORDERED` / `CANCELED`. 취소는 허브 이동 시작 전 + 업체 배송 출발 전에만 가능, 취소 시 공급업체 허브 재고 복원. 삭제는 MASTER만, 취소 또는 배송 완료된 주문만(`ORDER_NOT_DELETABLE`).
- **업체 간 배송 구조**: 공급업체 → 공급업체 허브 → (허브 이동) → 요청업체 허브 → 요청업체. `p_delivery_routes`는 허브 이동 구간 **0~1행**(같은 허브면 0행, `sequence`는 중계 확장 대비).
- `p_deliveries` 컬럼: `order_id` UK, `supplier_company_id`, `receiver_company_id`, `status`, `departure_hub_id`(공급업체 허브), `destination_hub_id`(요청업체 허브), `delivery_address`(기본값 요청업체 주소), `recipient_name`(기본값 요청업체명), `manager_id`(업체 배송 담당자), `out_for_delivery_at`, `delivered_at`
- `p_delivery_routes` 컬럼: `delivery_id` FK, `sequence`, `departure_hub_id`, `arrival_hub_id`, `expected/actual_distance_km`, `expected/actual_duration_minutes`, `status`, `manager_id`(허브 이동 담당자)
- 배송 상태:
  - 다른 허브: `HUB_WAITING` → `HUB_MOVING` → `DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY` → `DELIVERED`
  - 같은 허브: 생성 즉시 `DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY` → `DELIVERED`
  - 허브 이동 구간 상태(`WAITING`/`MOVING`/`ARRIVED`/`CANCELED`)가 배송 상태와 1:1 자동 연동 (D-07)
  - 전제 조건: `MOVING`은 허브 이동 담당자 배정 필수, `OUT_FOR_DELIVERY`는 `DEST_HUB_ARRIVED` + 업체 배송 담당자 배정 필수. 출발·완료 시 각 시각 기록
  - `CANCELED`는 `HUB_WAITING` 또는 **허브 이동 없이 시작한** `DEST_HUB_ARRIVED`에서만. 허브 이동을 마치고 도착한 배송은 취소 불가 (재고는 공급업체 허브로 복원되는데 실물은 요청업체 허브에 있어 불일치)
- 배송 조회(D-01, D-02) 권한: MASTER 전체 / 배송담당자는 본인 담당(업체 배송 또는 허브 이동)만 / 업체 관리자는 공급·수령업체가 본인 업체인 배송만 (I-14로 관리 업체 조회)
- **상태 전이 검증은 Entity 메서드에 둔다.** 위반 시 409 `INVALID_STATUS_TRANSITION`.

## 8. 코드 구조 규칙
- 이 디렉터리가 Gradle 루트(`rootProject.name = 'sparta-logistics'`): `common`, `eureka-server`, `gateway-service`, `user-service`, `hub-service`, `company-service`, `order-service`, `delivery-service`, `infra/postgres/init.sh`(SA의 init.sql을 환경 변수 비밀번호 사용을 위해 셸로 작성), `Docs/`(설계 문서, 원본은 `Docs/reference/`), `docker-compose.yml`, `Dockerfile`(전 모듈 공용 멀티 스테이지, `MODULE` 인자), `script/run.sh`
- `common` 모듈: `BaseEntity`, `ApiResponse`, `PageResponse`, `GlobalExceptionHandler`, `ErrorCode`, `UserContext`/`@CurrentUser`, `FeignHeaderInterceptor`
- 패키지: `com.sparta.logistics.{service}` 하위에 Layered Architecture
  - `presentation/` controller, request/response DTO
  - `application/` service(UseCase), command/result DTO, 트랜잭션 경계, Saga
  - `domain/` entity, enum, VO, repository **인터페이스**
  - `infrastructure/` JPA·QueryDSL 구현, FeignClient, Slack client, config
- 의존 방향: presentation → application → domain, infrastructure는 domain 인터페이스를 구현(의존성 역전)
- **Entity는 Controller 밖으로 노출 금지**, 계층별 DTO 분리

### 8-1. 구현 컨벤션 (초기 세팅 기준, 반드시 준수)
- 버전: Spring Boot 3.5.16, Spring Cloud 2025.0.3, Gradle 8.14.3(wrapper), QueryDSL 5.1.0(jakarta), JJWT 0.12.6, springdoc 2.8.13. 버전은 루트 `build.gradle`의 `ext`에서만 관리.
- 비즈니스 서비스는 **`common` 하나만 의존**하면 Web·Validation·JPA·OpenFeign·QueryDSL·Swagger·Eureka Client·PostgreSQL이 함께 들어온다 (루트 `build.gradle`에서 주입). 공통 설정은 `CommonAutoConfiguration`이 자동 등록되므로 `scanBasePackages` 지정 금지.
- `gateway-service`는 WebFlux이므로 `common`을 의존하지 않는다 (서블릿 의존성 충돌 방지).
- **응답**: 외부 API(`/api/v1/**`)는 `ApiResponse.ok/created(...)`로 감싸고, 목록은 `PageResponse.from(page, mapper)`. **내부 API(`/internal/**`)는 DTO를 래퍼 없이 그대로 반환**(Feign 역직렬화 단순화).
- **페이징**: 서비스 계층에서 `Pageables.normalize(pageable)` 호출 (size 10/30/50 외는 10, 정렬 없으면 `createdAt DESC`).
- **예외**: 서비스별 에러 코드는 `ErrorCode`를 구현한 enum(예: `UserErrorCode.USER_NOT_FOUND`)으로 정의하고 `throw new BusinessException(code)`. 공통 코드는 `CommonErrorCode`(VALIDATION_ERROR, INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN, INVALID_STATUS_TRANSITION, SERVICE_UNAVAILABLE 등).
- **Feign 에러 전파**: 하위 서비스 4xx는 코드·상태 그대로 전달(예: hub의 409 `STOCK_INSUFFICIENT` → order 응답도 409), 5xx·연결 실패·타임아웃은 503 `SERVICE_UNAVAILABLE` (`FeignErrorDecoder`, `GlobalExceptionHandler`). X-User-* 헤더는 `FeignHeaderInterceptor`가 자동 전파. Feign 재시도는 기본 꺼짐 → 멱등 API 클라이언트에만 개별 설정.
- **사용자 식별**: 컨트롤러 파라미터 `@CurrentUser UserContext user` (`isMaster()`, `isSelf(id)`). 헤더 없으면 401.
- **MASTER 전용 API**: 컨트롤러 메서드(또는 클래스)에 `@MasterOnly` → MASTER가 아니면 403 `FORBIDDEN` (common 인터셉터). 권한 규칙은 Gateway가 아니라 API 선언부에 둔다. 소유자 검증(본인·업체 관리자 등)은 서비스 계층에서 직접 한다.
- **회원가입 검증**: SA 기준 — username 4~10자. 그 외 형식 규칙은 추후 결정. 가입 시 권한은 항상 `USER`.
- **초기 MASTER 계정**: user-service 기동 시 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`로 없으면 생성 (비워 두면 생략).
- **부분 유니크 인덱스**: `src/main/resources/db/indexes.sql`에 `CREATE UNIQUE INDEX IF NOT EXISTS ... WHERE deleted_at IS NULL`로 작성. Hibernate DDL 이후 실행되도록 `spring.jpa.defer-datasource-initialization: true`, `spring.sql.init.mode: always`, `spring.sql.init.data-locations: classpath:db/indexes.sql` 설정 (user-service 참고).
- **엔티티**: `extends BaseEntity`, `@Table(name = "p_...")`, `@SQLRestriction("deleted_at IS NULL")`(논리 삭제 행 제외, 엔티티마다 직접 선언), PK는 `@GeneratedValue(strategy = GenerationType.UUID)` — **단, `Order`는 Saga를 위해 서비스에서 선발급**. 삭제는 `softDelete(userId)`.
- **스키마 생성**: 현재 `ddl-auto: update` (개발용). 부분 유니크 인덱스(`WHERE deleted_at IS NULL`) 등 JPA로 만들 수 없는 제약은 별도 SQL로 추가 예정.
- **JWT 계약** (user-service 발급 ↔ gateway 검증): HS256, 키는 `JWT_SECRET`(Base64, 32바이트 이상), `sub` = userId(UUID), claim `username`, `role`(MASTER/USER), `iat`, `exp`, 만료 `JWT_EXPIRATION_MS`(기본 60분). JWT 키 관리 방식은 팀 논의 후 변경될 수 있음.
- **Gateway 권한 재확인**: I-01로 사용자 존재·권한을 확인하고 결과를 **userId 기준 60초 캐시**(Caffeine). I-01 응답의 `userId, username, role, isDeliveryManager`로 헤더를 주입한다 (탈퇴 404 → 401, 권한 불일치 → 401 `TOKEN_ROLE_MISMATCH`, user-service 장애 → 503). 캐시 때문에 권한 변경·탈퇴는 최대 60초 늦게 반영된다.

### 8-2. 실행 방법
- 최초 1회: `cp .env.example .env` 후 `JWT_SECRET` 채우기 (`openssl rand -base64 32`)
- 전체 실행: `docker compose up -d --build` (Dockerfile이 멀티 스테이지라 컨테이너 안에서 Gradle 빌드, 호스트에 JDK 불필요). `script/run.sh`는 같은 동작의 단축 명령. 확장: `docker compose up -d --scale order-service=2`
- IDE에서 일부 서비스만 실행: `docker compose up -d postgres eureka-server` 후 IDE에서 실행 (yml이 루트 `.env`를 자동으로 읽음)
- 확인: Eureka `http://localhost:8761`, Swagger `http://localhost:8080/swagger-ui.html`(우측 상단에서 서비스 선택)
- 새로 뜬 인스턴스가 Gateway·Feign에 반영되기까지 최대 수십 초 걸릴 수 있다 (Eureka 갱신 5초 + LB 캐시 5초로 단축해 둠)
- 초기 MASTER: `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD` (기본 `master` / `master_local_pw`)로 로그인
- Windows Git Bash의 curl로 한글이 든 JSON을 `-d '...'`로 보내면 UTF-8이 깨져 400이 난다 → UTF-8 파일로 저장 후 `--data-binary @file.json` 사용 (Swagger·Postman은 문제없음)

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
- hub-service 기동 시 허브 17개·경로 272개 시드 적재.
- **JWT secret, DB 비밀번호, Slack Webhook URL은 `.env` → 환경 변수로 주입, Git 커밋 금지.**
- Swagger: 각 서비스 springdoc 문서를 Gateway Swagger UI에서 서비스별로 조회.

## 10. 결정 이력 / 미결정 사항

### 확정
- **상품은 공급업체 소속 허브에만 존재** (회의 결정) → 다중 허브 할당·"가까운 허브 순" 로직 폐기, 출고 허브 = 공급업체 허브 (§4)
- `p_hub_stock_histories` → **확장 기능으로 제외**, 대체로 **`p_stock_allocations` 도입** (§4)
- H-05 허브 재고 검색 → **구현 범위에서 제외**
- I-02 `/internal/hubs/{hubId}` 호출자에 **user-service 추가** (배송담당자 지정 시 소속 허브 확인)
- I-03은 단일 경로 조회(`departureHubId`, `arrivalHubId`), I-05 요청은 `orderId, productId, quantity`
- 배송담당자: **MASTER 수동 배정** + `isDeliveryManager` 기준, D-04·D-06 모두 **PATCH**, 자동 배정 없음
- `p_orders.total_price` **저장** (§3-13)
- Saga **오케스트레이션** (§5)
- Feign 타임아웃 (§9)
- 컬럼 length → §11 기준 적용
- 업체 간 배송: 경로 테이블 유지(0~1행), 배송 컬럼 추가, 상태 전이, 조회 권한 (§7). I-09 요청에 공급·요청업체 ID 추가, **I-14 `GET /internal/companies?managerId=` 신설** (company → delivery)

### 미결정 (구현 전 사용자 확인 필요)
- 없음

## 11. 컬럼 길이 기준 (임의 지정, 필요 시 조정)
- 공통: enum 성격 컬럼(`role`, `status`, `affiliation_type` 등) `varchar(20)`, 주소 `varchar(255)`, Audit `created_by`/`updated_by`/`deleted_by`는 `uuid`
- `p_users`: `username varchar(10)`(4~10자), `password varchar(100)`(BCrypt 60자 + 여유), `name varchar(50)`, `slack_id varchar(50)`, `affiliation_name varchar(100)`
- `p_hubs`: `name varchar(50)`, `latitude numeric(10,7)`, `longitude numeric(10,7)`
- `p_hub_routes`: `distance_km numeric(8,2)`, `duration_minutes int`
- `p_companies`: `name varchar(100)`
- `p_products`: `name varchar(100)`, `description text`, `price bigint`(≥0)
- `p_orders`: `product_name varchar(100)`, `request_note varchar(500)`, `unit_price bigint`, `total_price bigint`(≥0)
- `p_deliveries`: `recipient_name varchar(100)`(기본값이 업체명이므로 업체명 길이에 맞춤), `delivery_address varchar(255)`, `status varchar(30)`, `out_for_delivery_at`/`delivered_at timestamp`
- `p_delivery_routes`: `expected/actual_distance_km numeric(8,2)`
