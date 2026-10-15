# 공통 규칙

모든 코드에 적용한다. 패키지와 의존 방향은 `docs/agents/coding-conventions.md` 1절을 따른다.

## 계층별로 두는 것
| 계층 | 패키지 | 두는 것 | 두지 않는 것 |
|---|---|---|---|
| presentation | `presentation`, `presentation/dto` | `{X}Controller`, `Internal{X}Controller`, `{X}Request`, `{X}Response` | Repository·Entity 직접 사용 |
| application | `application`, `application/dto` | `{X}Service`, `{X}QueryService`, `{X}Command`, `{X}Result`, 외부 호출 인터페이스 | Request·Response DTO, JPA·Feign 구현 |
| domain | `domain` (많아지면 `domain/model`, `domain/repository` 등으로 나눠도 된다) | Entity, enum, Repository 인터페이스, `{Service}ErrorCode` | Spring Data·Feign 의존 |
| infrastructure | `infrastructure/persistence`, `infrastructure/client`, `infrastructure/config` | Spring Data·QueryDSL 구현, FeignClient, 설정 빈 | 비즈니스 규칙 |

외부 의존(토큰 발급, 다른 서비스 호출, Slack)은 application에 인터페이스를 두고 infrastructure에서 구현한다. 예: `application/TokenIssuer` ← `infrastructure/security/JwtProvider`.

## common에 이미 있는 것
| 필요한 것 | 쓸 것 |
|---|---|
| Audit 컬럼·논리 삭제 | `BaseEntity` 상속, `softDelete(username)` |
| 성공 응답 | `ApiResponse.ok(data)`, `ApiResponse.ok(message, data)`, `ApiResponse.created(message, data)` |
| 목록 응답 | `PageResponse.from(page, XResponse::from)` |
| 예외 | `new BusinessException(XxxErrorCode.…)`, 공통은 `CommonErrorCode` |
| 현재 사용자 | `@CurrentUser UserContext user` |
| 역할 확인 | `@RequireRole(Role.MASTER)` |
| 소유자 확인 | `user.validateSelfOrMaster(ownerId)`, `user.validateMaster()`, `user.isSelfOrMaster(ownerId)` |
| 페이지·정렬 | `Pageable` 파라미터(공통 규칙 자동 적용), `SortAllowList.check(...)`, `QuerydslSupport.toOrderSpecifiers(...)` |
| Feign 헤더 전달·에러 변환 | 자동 적용(ADR 0009). 직접 만들지 않는다 |

common에는 도메인 로직과 서비스별 DTO를 넣지 않는다. common을 바꾸면 `docs/services/common/README.md`를 함께 고친다.

## 예외
- 던지는 예외는 `BusinessException` 하나다. `RuntimeException`, `IllegalArgumentException`, `EntityNotFoundException`을 직접 던지지 않는다.
- 에러 코드는 `{Service}ErrorCode implements ErrorCode` enum에 두고, 이름 규칙과 목록은 `docs/error-codes.md`를 따른다.
- 예외를 잡아 로그만 남기고 삼키지 않는다.

## 네이밍
| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | 역할 접미사 | `HubController`, `HubService`, `HubQueryService`, `HubRepository`, `HubJpaRepository`, `HubClient` |
| DTO | 계층별 접미사, `record` | `CreateHubRequest` → `CreateHubCommand` → `HubResult` → `HubResponse` |
| 메서드 | 조회는 `get`(없으면 예외), `find`(Optional), `search`(목록) | `getHub`, `findByName`, `searchHubs` |
| 상수 | UPPER_SNAKE | `SORTABLE_FIELDS` |

- Javadoc과 주석은 한국어로 쓰고, API와 테이블에는 SA 번호를 붙인다(`/** H-02 허브 단건 조회 */`, `(SA 5.3)`).
- 시각은 `LocalDateTime`(DB `TIMESTAMP`)을 쓴다.
