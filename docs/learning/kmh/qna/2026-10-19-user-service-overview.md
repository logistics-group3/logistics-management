---
title: user-service 구현 둘러보기
date: 2026-10-19
updated: 2026-10-19
asker: kmh
level: 중급
tags: [user-service, layered-architecture, jpa, querydsl, feign, transaction, security]
---

# user-service 구현 둘러보기

## 질문
구현된 user-service가 어떻게 되어 있는지 설명해 달라.

## 짧은 답
user-service는 회원가입, 로그인(JWT 발급), 사용자 관리(U-01~U-08), 배송담당자 지정, 다른 서비스용 내부 조회(I-01, I-14, I-16)를 맡는다. 토큰 검증은 맡지 않는다. Gateway가 한다.

구조는 `presentation → application → domain ← infrastructure` 4계층이다. 공통 기능(헤더로 받은 사용자, 역할 검사, 예외 처리, Audit, 페이징)은 `common` 모듈에서 가져다 쓴다. 코드 곳곳에 보안 함정(계정 존재 노출, 권한 상승), 동시성(중복 가입), 트랜잭션 범위(외부 호출과 DB 커넥션)를 막는 장치가 있다.

## 자세히

### 1. 패키지 구조
```
user-service/src/main/java/com/sparta/logistics/user
├─ presentation    AuthController, UserController, InternalUserController, dto/(Request·Response)
├─ application     AuthService, UserService, UserRoleService, UserQueryService
│                  TokenIssuer, HubReader (인터페이스: 외부 기술을 숨긴다), dto/(Command·Result)
├─ domain          User(엔티티), UserRepository, UserErrorCode, AffiliationType, UserSearchCondition
└─ infrastructure  security/JwtProvider, client/HubClient(Feign)·HubClientAdapter,
                   persistence/UserJpaRepository·UserQueryRepositoryImpl(QueryDSL),
                   init/MasterAccountInitializer
```
- application은 JWT 라이브러리와 Feign을 모른다. `TokenIssuer`, `HubReader` 인터페이스만 알고, 구현(`JwtProvider`, `HubClientAdapter`)은 infrastructure에 있다. 의존성 역전(DIP)이다.
- 이렇게 나누면 테스트에서 `HubReader`를 목으로 바꿔 hub-service 없이 U-07을 검증할 수 있다. 호출 방식을 바꿔도 application 코드는 그대로다.
- DTO는 계층마다 나뉜다. `SignupRequest`(presentation, 입력 검증) → `SignupCommand`(application 입력) → `SignupResult`/`UserResult`(application 출력) → `UserResponse`(presentation 응답).

### 2. 데이터: `p_users` 테이블과 `User` 엔티티
| 컬럼 | 설명 |
|---|---|
| `user_id` UUID | PK. `GenerationType.UUID` |
| `username` | `^[a-z0-9]{4,10}$`. 일반 UNIQUE 제약이라 탈퇴 후에도 재사용할 수 없다 |
| `password` | BCrypt 해시 |
| `name`, `affiliation_type`(COMPANY/HUB), `affiliation_name` | 프로필, 소속 |
| `slack_id`, `slack_email` | Slack DM용. 신규 가입은 이메일만 받는다(V2, V3, ADR 0016) |
| `role` | `MASTER`, `USER`. CHECK 제약 |
| `is_delivery_manager`, `hub_id` | 배송담당자 여부와 담당 허브. CHECK: 배송담당자면 `hub_id`가 필수 |
| `created_*`, `updated_*`, `deleted_*` | `BaseEntity`(common)의 감사 컬럼, 논리 삭제 |

- `@SQLRestriction("deleted_at IS NULL")`: JPA와 QueryDSL 조회에 자동으로 붙어 탈퇴자를 뺀다. 그래서 탈퇴자의 I-01 조회는 404가 되고, Gateway는 그 사용자의 토큰을 거부한다.
- 중복 확인만은 탈퇴자까지 봐야 한다. 그래서 `existsByUsernameIncludingDeleted`를 따로 둔다.
- `hub_id`는 hub-service 테이블을 가리키지만 FK가 없다. 서비스마다 스키마가 따로라서 논리 참조만 한다(ADR 0012). 존재 확인은 hub-service I-02 호출로 한다.
- 스키마 변경은 Flyway 마이그레이션(`db/migration/V{n}__*.sql`)으로만 한다.

**엔티티에 규칙을 둔다.** setter 대신 의도가 드러나는 메서드를 쓴다.
- `User.signup(...)`: 항상 `Role.USER`로 시작한다. 가입 API로 MASTER를 만들 수 없다.
- `User.master(...)`: 초기 관리자 전용이다.
- `updateProfile(...)`: null인 값은 바꾸지 않는다. 배송담당자의 소속은 바꿀 수 없다.
- `assignDeliveryManager(hubId, hubName)`: 배송담당자로 지정하면서 소속을 그 허브로 함께 바꾼다.
- `releaseDeliveryManager()`: 담당 허브만 지우고 소속은 둔다.

### 3. API 목록
| ID | 메서드, 경로 | 권한 | 구현 |
|---|---|---|---|
| U-01 | `POST /api/v1/auth/signup` | 공개 | `AuthService.signup` |
| U-02 | `POST /api/v1/auth/login` | 공개 | `AuthService.login` → JWT |
| U-03 | `GET /api/v1/users/me` | 로그인 | `UserQueryService.getMyInfo` |
| U-04 | `GET /api/v1/users/{id}` | 본인 또는 MASTER | `getUser` (서비스 계층에서 소유자 검사) |
| U-05 | `GET /api/v1/users` | MASTER | `search` (QueryDSL 동적 검색, 페이징) |
| U-06 | `PATCH /api/v1/users/{id}` | MASTER | `UserService.update` |
| U-07 | `PATCH /api/v1/users/{id}/role` | MASTER | `UserRoleService.changeRole` (hub-service 호출) |
| U-08 | `DELETE /api/v1/users/{id}` | MASTER | `UserService.delete` (논리 삭제) |
| I-01 | `GET /internal/users/{id}` | 내부 | Gateway 권한 재확인, delivery |
| I-14 | `GET /internal/users/delivery-managers?hubId=` | 내부 | delivery: 허브별 배송담당자 |
| I-16 | `GET /internal/users/{id}/notification-contact` | 내부 | order: Slack DM 연락처 |

- 외부 API는 `ApiResponse`로 감싸서 응답한다. 내부 API는 DTO를 그대로 반환한다(ADR 0010).
- `/internal/**`은 Gateway에 라우트가 없고, Gateway 필터가 404로 막는다. 외부에서는 호출할 수 없다.
- 역할 검사는 `@RequireRole(Role.MASTER)`이 하고, 현재 사용자는 `@CurrentUser UserContext`로 받는다. 둘 다 common 모듈 기능이다.

### 4. 기능별 핵심 구현
**회원가입 (U-01)**
- 입력 검증은 `SignupRequest`의 Bean Validation이 한다. username은 소문자·숫자 4~10자다. 비밀번호는 8~15자이고 대문자, 소문자, 숫자, 특수문자를 각각 하나 이상 포함해야 한다.
- 중복 확인과 저장 사이에 같은 username 가입이 끼어들 수 있다. 최종 방어선은 DB UNIQUE 제약이다. 위반 예외를 잡아 같은 409 `DUPLICATE_USERNAME`으로 바꾼다.
- `save()`가 아니라 `saveAndFlush()`를 쓴다. `save()`는 INSERT를 커밋 시점까지 미루기 때문에, 예외가 try 블록 밖(트랜잭션 프록시)에서 터진다.

**로그인 (U-02)**
- 없는 사용자, 탈퇴자, 비밀번호 불일치를 모두 같은 `LOGIN_FAILED`(401)로 응답한다.
- 사용자가 없어도 더미 해시와 BCrypt 비교를 한다. 비교를 건너뛰면 응답이 빨라져, 응답 시간으로 계정 존재를 알아낼 수 있기 때문이다(타이밍 공격).
- `JwtProvider`가 HS256 토큰을 발급한다. claim은 `sub`=userId, `username`, `role`, `iat`, `exp`(3600초)다.

**단건 조회 (U-04): 권한 검사를 조회보다 먼저**
`user.validateSelfOrMaster(userId)`를 DB 조회 전에 한다. 조회를 먼저 하면 USER가 남의 ID를 넣었을 때 있으면 403, 없으면 404가 나온다. 응답 코드만으로 다른 사용자 ID가 존재하는지 알 수 있게 된다.

**검색 (U-05)**
- QueryDSL `BooleanExpression`을 조건별로 만든다. 값이 없으면 null을 돌려주고, `where`는 null 조건을 무시한다. 이렇게 동적 쿼리를 만든다.
- 정렬은 `createdAt`, `username`만 허용한다. password로 정렬을 허용하면 정렬 순서로 해시 값을 추측할 수 있다.
- `PageableExecutionUtils.getPage`는 마지막 페이지처럼 전체 개수를 이미 알 수 있으면 count 쿼리를 생략한다.

**권한·배송담당자 지정 (U-07): 트랜잭션 밖에서 외부 호출**
```
UserRoleService.changeRole (트랜잭션 없음)
  ├ 자기 자신의 role 변경 차단
  ├ hubReader.getHub(hubId)        ← hub-service I-02 (Feign, GET이라 재시도 허용)
  └ userService.applyRoleChange()  ← @Transactional, DB 변경만 짧게
```
- 트랜잭션 안에서 hub-service를 부르면, 응답을 기다리는 동안(read 20초 × 재시도) DB 커넥션을 붙잡는다. 호출이 몰리면 커넥션 풀이 바닥난다.
- 같은 클래스 안에서 `@Transactional` 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 시작되지 않는다(self-invocation). 그래서 클래스를 둘로 나눴다.
- 배송담당자가 아닌 사용자에게 `hubId`만 보내면 400이다. 지정하려는 것인지 실수인지 모호하기 때문이다(user 결정 0001).

**비활성화 (U-08)**
- 논리 삭제(`softDelete`)다. `deleted_at`, `deleted_by`를 채운다.
- 자기 자신은 삭제할 수 없다. 마지막 MASTER가 스스로를 지워 관리자가 사라지는 일을 막는다.

**초기 MASTER (`MasterAccountInitializer`)**
- 가입 API로는 MASTER를 만들 수 없다. 그래서 기동할 때 환경 변수 `MASTER_USERNAME`, `MASTER_PASSWORD`로 만든다(`ApplicationRunner`).
- 이미 같은 username이 있으면 아무것도 하지 않는다. 여러 인스턴스가 동시에 떠도 UNIQUE 제약이 중복을 막는다.
- 메서드에 `@Transactional`을 걸지 않는다. 걸면 저장 실패 시 트랜잭션이 rollback-only로 표시된다. 예외를 잡아도 메서드가 끝날 때 커밋하면서 `UnexpectedRollbackException`이 난다.

### 5. 다른 서비스와의 관계
```
Gateway ──I-01──▶ user-service ──I-02──▶ hub-service
delivery ─I-01, I-14─▶
order ────I-16────▶
```
- I-01 응답 필드를 바꾸면 Gateway의 `UserSnapshot`이 깨질 수 있다. 협업 로그로 알려야 한다.
- I-14는 담당자가 없으면 빈 배열을 준다. "담당자가 없으면 실패"라는 판단은 호출하는 delivery-service의 업무 규칙이라, user-service는 사실만 돌려준다.

### 6. 테스트
| 종류 | 예 | 확인하는 것 |
|---|---|---|
| 도메인 단위 | `UserTest` | 엔티티 규칙(배송담당자 소속 변경 금지 등) |
| 서비스 단위 | `AuthServiceTest`, `UserRoleServiceTest` | 목(mock)으로 분기 검증 |
| 웹 슬라이스 | `UserControllerWebMvcTest` | 입력 검증, 권한, 응답 형식 |
| 통합 | `UserAuthIntegrationTest`, `UserCommandIntegrationTest` | Testcontainers PostgreSQL로 실제 DB 제약과 마이그레이션 |

실행: `./gradlew :user-service:test`. 통합 테스트는 Docker가 켜져 있어야 한다.

### 직접 해보기
1. `script/run.sh`로 전체를 띄운다.
2. Swagger UI(`http://localhost:8080/swagger-ui.html`)에서 `user-service`를 고르고, U-01로 가입한 뒤 U-02로 토큰을 받는다.
3. `Authorize`에 토큰을 넣고 U-03을 호출한다. 확인: 내 정보가 나온다.
4. 같은 토큰으로 U-05(검색)를 호출한다. 확인: USER라서 403 `FORBIDDEN`이 나온다.
5. `.env`의 MASTER 계정으로 로그인해 U-07로 위 사용자를 MASTER로 바꾼다. 그 직후 USER 토큰으로 요청한다. 확인: 401 `TOKEN_ROLE_MISMATCH`가 나온다. Gateway 캐시 때문에 최대 60초 늦을 수 있다.

### 더 깊이
- 토큰 검증과 권한 재확인 흐름은 `2026-10-19-gateway-user-auth-flow.md`에 있다.
- 결정 기록: `docs/services/user/decisions/0001-user-management-guards.md`(수정·삭제 보호 규칙), `docs/decisions/0010`(내부 API 응답 형식), `0012`(스키마 분리와 UUID 논리 참조), `0016`(Slack DM).

## 면접 포인트
- "외부 API 호출을 트랜잭션 안에서 하면 안 되는 이유": 응답을 기다리는 동안 DB 커넥션을 잡아 커넥션 풀이 고갈된다. 외부 조회를 먼저 끝내고 DB 변경만 짧은 트랜잭션으로 묶었다. 같은 클래스 내부 호출은 프록시를 거치지 않으므로 클래스를 나눴다.
- "중복 가입을 어떻게 막나": 애플리케이션 존재 확인만으로는 동시 요청을 막지 못한다. DB UNIQUE 제약을 최종 방어선으로 두고, 위반 예외를 409로 바꾼다.
- "로그인 실패 응답을 왜 하나로 했나": 계정 존재 여부 노출(사용자 열거)을 막기 위해서다. 응답 시간 차이까지 더미 해시 비교로 없앴다.
- 꼬리 질문: 논리 삭제를 쓸 때 username UNIQUE를 부분 인덱스로 하지 않은 이유. 탈퇴 후에도 username을 재사용할 수 없다는 요구사항(SA 5.2) 때문이다.
