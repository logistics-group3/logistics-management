# 스파르타 물류 — 공통 인프라

MSA 기반 B2B 물류 관리·배송 시스템의 **공통 인프라 스켈레톤**입니다.
범위는 Notion 「공통 인프라 개발 범위」, 세부 규칙은 「스파르타물류_SA문서_Master」 v1.1을 따릅니다.

| 항목 | 값 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 (Boot 3.5 계열 최신) |
| JJWT | 0.12.6 |
| DB | PostgreSQL 16 (컨테이너 1개 + 서비스별 스키마·계정) |

버전은 `gradle.properties` 한 곳에서만 관리합니다.

---

## 1. 빠른 시작

```bash
cp .env.example .env          # 처음 한 번
docker compose up -d --build  # 또는 ./script/run.sh
./script/smoke-test.sh        # 완료 기준 6가지 자동 확인
```

- 처음 빌드할 때는 Gradle 의존성을 받느라 5~10분 정도 걸립니다. 두 번째부터는 BuildKit 캐시를 써서 빨라집니다.
- Eureka 대시보드: http://localhost:8761
- Gateway(유일한 API 진입점): http://localhost:8080
- 초기 MASTER 계정: `master` / `.env`의 `MASTER_PASSWORD` (기본값 `Master!2026`)

### IDE에서 서비스 하나만 띄워 개발할 때

```bash
docker compose up -d postgres eureka-server gateway-service user-service
```

그다음 개발 중인 서비스(예: `HubServiceApplication`)만 IntelliJ에서 실행합니다.
`application.yml`의 기본값이 `localhost:5432`와 `localhost:8761`을 바라보고 있어서 별도 설정은 필요 없습니다.

> 주의: 같은 서비스를 Docker와 IDE에서 동시에 띄우면 Eureka에 두 인스턴스가 등록되고, Gateway가 번갈아 호출합니다.
> 그럴 때는 `docker compose stop hub-service`로 Docker 쪽 인스턴스를 내려두세요.

---

## 2. 완료 기준 확인 (수요일 리뷰용)

`./script/smoke-test.sh`가 아래 항목을 curl로 확인하고 통과/실패 개수를 출력합니다.

| # | 완료 기준 | 확인 방법 |
|---|---|---|
| 1 | `docker compose up` 한 번으로 전체 기동, Eureka 등록 | `/eureka/apps`에 서비스 6개 |
| 2 | 회원가입 → 로그인 → 토큰 발급 | 201 → 200 + accessToken |
| 3 | 토큰으로 Gateway 경유 시 서비스에서 `@CurrentUser` 수신 | `GET /api/v1/hubs/sample/me` |
| 4 | 토큰 없음·위조 → 401, USER가 MASTER API → 403 | `GET /api/v1/hubs/sample/master` |
| 5 | 외부에서 `/internal/**` 호출 차단 | 404 |
| 6 | 클라이언트가 보낸 `X-User-Role: MASTER` 무시 | USER 토큰 + 위조 헤더 → 403 |

> `hub-service`의 `SampleController`는 검증용입니다. hub 도메인 개발을 시작할 때 삭제하세요.

---

## 3. 프로젝트 구조

```
sparta-logistics/
├── common/                    # 공통 라이브러리 (Auto-Configuration 으로 자동 적용)
├── eureka-server/             # 8761
├── gateway-service/           # 8080  라우팅, JWT 검증, 권한 재확인, 헤더 정리, /internal 차단
├── user-service/              # 8081  회원가입(U-01), 로그인(U-02), 내부 사용자 조회(I-01)
├── hub-service/               # 8082  빈 모듈 + 검증용 SampleController
├── company-service/           # 8083  빈 모듈
├── order-service/             # 8084  빈 모듈
├── delivery-service/          # 8085  빈 모듈
├── docker/Dockerfile          # 모든 서비스 공용 Dockerfile 템플릿 (MODULE 빌드 인자)
├── infra/postgres/init.sql    # 스키마 5개 + 서비스 전용 계정
├── script/run.sh, smoke-test.sh
├── docker-compose.yml
└── .env.example
```

서비스 내부 패키지 규칙 (SA 2.4):

```
com.sparta.logistics.{서비스}
├── presentation/     controller, dto(request/response)
├── application/      service, dto(command/result)
├── domain/           entity, enum, repository(interface), ErrorCode
└── infrastructure/   jpa, querydsl, client(feign), config
```

`user-service`가 이 구조로 구현된 참고 예시입니다.

---

## 4. common 모듈 사용법

`implementation project(':common')`만 추가하면 됩니다. 각 기능은 해당 라이브러리가 있는 서비스에서만 자동으로 켜집니다. `@ComponentScan`이나 `@Import`는 필요 없습니다.

### 응답

```java
return ApiResponse.ok(data);                                  // { status: 200, message: "SUCCESS", data }
return ApiResponse.ok(PageResponse.from(page, XxxResponse::from));  // 목록

@ResponseStatus(HttpStatus.CREATED)
return ApiResponse.created("업체가 생성되었습니다.", data);       // 201

Pageable p = Pageables.normalize(pageable, Set.of("createdAt", "name"));  // size 10/30/50 보정, 정렬 허용 필드
```

### 에러

도메인 에러 코드는 서비스마다 `ErrorCode`를 구현한 enum으로 만들고, 예외는 `BusinessException`만 던지면 됩니다.

```java
@Getter @RequiredArgsConstructor
public enum HubErrorCode implements ErrorCode {
    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
    STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "허브 재고가 부족합니다.");

    private final HttpStatus status;
    private final String message;
    public String getCode() { return name(); }
}

throw new BusinessException(HubErrorCode.HUB_NOT_FOUND);
```

이렇게 던진 예외는 SA 6.1 형식(`status, code, message, path, timestamp, errors`)으로 변환됩니다.
`@Valid` 검증에 실패하면 `VALIDATION_ERROR`와 `errors` 배열로 응답합니다.

### 사용자 정보 / 권한

```java
@GetMapping("/{orderId}")
public ApiResponse<OrderResponse> get(@PathVariable UUID orderId, @CurrentUser UserContext user) {
    // user.userId(), user.username(), user.role(), user.deliveryManager(), user.isMaster()
}

@RequireRole(UserRole.MASTER)   // 역할 불일치 → 403, 헤더 없음 → 401
@PostMapping("/{hubId}/stocks/inbound")
public ApiResponse<...> inbound(...) { }
```

소유자 검증(업체 `manager_id`, 주문 `orderer_id` 등)은 Application 계층에서 직접 처리합니다.

### 엔티티

```java
@Entity
@Table(name = "p_hubs")
@SQLRestriction("deleted_at IS NULL")    // 엔티티마다 반드시 붙이기 (상속되지 않음)
public class Hub extends BaseEntity {     // 기록형 데이터는 BaseCreatedEntity
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hub_id")
    private UUID id;
}

hub.softDelete(user.username());          // 논리 삭제
```

- `created_by`, `updated_by`는 `X-Username` 헤더 값으로 자동 기록됩니다. 헤더가 없으면 `SYSTEM`이 들어갑니다.
- `@EnableJpaAuditing`은 common이 이미 켜 두었습니다. 서비스에 다시 붙이면 빈이 중복되어 기동에 실패합니다.
- `order_id`처럼 저장 전에 미리 발급해야 하는 PK는 `@GeneratedValue` 대신 직접 생성하고 `Persistable`을 구현하세요. 그래야 `save` 시 불필요한 select가 생기지 않습니다.

### Feign

```java
@FeignClient(name = "hub-service")
public interface HubClient {
    @GetMapping("/internal/hubs/{hubId}")
    ApiResponse<HubInfo> getHub(@PathVariable UUID hubId);
}
```

- 현재 요청의 `X-User-*` 헤더가 자동으로 전달됩니다.
- 하위 서비스의 4xx 에러는 같은 code로 다시 던져집니다 (예: `404 HUB_NOT_FOUND`).
- 5xx·연결 실패·타임아웃은 `503 SERVICE_UNAVAILABLE`로 바뀝니다.
- 타임아웃은 connect 2초, read 5초입니다 (`application.yml`).

---

## 5. Gateway 처리 순서 (SA 8.3)

1. 클라이언트가 보낸 `X-User-Id`, `X-Username`, `X-User-Role`, `X-Delivery-Manager`를 제거합니다.
2. `/internal/**`이면 404로 응답합니다. 라우팅 전에 동작해야 해서 `WebFilter`로 구현했습니다.
3. `/api/v1/auth/signup`, `/api/v1/auth/login`, Swagger 경로는 인증 없이 통과합니다.
4. Bearer 토큰의 서명과 만료를 검증합니다. 실패하면 `401 UNAUTHORIZED`입니다.
5. user-service I-01로 role과 탈퇴 여부를 다시 확인합니다 (Caffeine 60초 캐시).
   - 불일치하거나 탈퇴했으면 `401 TOKEN_ROLE_MISMATCH`입니다.
   - user-service가 장애면 `503 SERVICE_UNAVAILABLE`입니다.
6. DB 기준 값으로 `X-User-*` 헤더를 다시 넣고 라우팅합니다.

---

## 6. SA 문서와 다르거나 새로 정한 부분 (리뷰 때 합의 필요)

| 항목 | 이 구현 | 이유 |
|---|---|---|
| I-01 명세 | `GET /internal/users/{userId}` → `userId, username, name, slackId, role, isDeliveryManager, hubId`. 탈퇴·없음은 404 | SA 6.3 표에 I-01 행이 비어 있음. gateway, company, delivery 호출자에게 필요한 값을 모음 |
| Dockerfile | 모듈마다 두지 않고 `docker/Dockerfile` 하나에 `MODULE` 인자를 받는 방식. 빌드 컨텍스트는 루트 | 멀티 모듈이라 `common`이 함께 필요함. 로컬에서 jar를 먼저 빌드하지 않아도 `docker compose up` 한 번으로 실행됨 |
| init.sql | `GRANT USAGE, CREATE` 대신 `CREATE SCHEMA ... AUTHORIZATION {서비스}_app` | 계정이 스키마 소유자가 되어 `ddl-auto`로 테이블을 만들 수 있음. 다른 스키마에는 여전히 권한 없음 |
| Spring Security | 전체 starter 대신 `spring-security-crypto`(BCrypt)만 사용 | 인증은 Gateway가 담당. starter를 넣으면 기본 필터 체인이 모든 요청을 막음 |
| 초기 MASTER 계정 | user-service 기동 시 `master` 계정 생성 | 회원가입은 항상 USER라서, 403 테스트와 관리자 API 개발용 MASTER가 필요 |
| Eureka 대기 | 서비스 `depends_on`의 eureka 조건을 `service_healthy`로 변경 | 기동 직후 등록 실패 로그를 줄임 |

---

## 7. 이번 범위에서 제외 (수요일부터 서비스 담당자)

- 도메인별 Entity·CRUD·검색, 서비스별 FeignClient
- Saga·보상 트랜잭션, 허브 초기 데이터(17개)·경로(272개), Slack 연동
- 시간이 남으면 할 일: QueryDSL 공통 설정, Swagger(springdoc) 공통 설정과 Gateway 통합 UI
- Config Server, front(3000)는 나중에 추가

---

## 8. 자주 겪는 문제

| 증상 | 해결 |
|---|---|
| `.env 파일이 없습니다` | `cp .env.example .env` |
| init.sql을 수정했는데 반영이 안 됨 | init.sql은 볼륨이 비어 있을 때만 실행됨. `docker compose down -v` 후 다시 실행 (DB 데이터 삭제됨) |
| 서비스 기동 직후 Gateway가 503 | Eureka에 등록되기 전 상태. 30초 정도 기다린 뒤 다시 시도 |
| 모든 요청이 `401 TOKEN_ROLE_MISMATCH` | DB를 초기화해서 토큰 속 사용자가 사라진 경우. 다시 로그인 |
| `WeakKeyException` | `JWT_SECRET`이 32바이트보다 짧음 |
| Windows에서 `./gradlew: not found` (Docker 빌드) | `.gitattributes`가 LF로 고정함. 이미 clone했다면 `git rm --cached -r . && git reset --hard` |
