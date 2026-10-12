# 공통 규칙 (항상 적용)

## 패키지 · 계층
`com.sparta.logistics.{service}` 아래 4계층. 의존 방향은 **presentation → application → domain ← infrastructure**.

| 계층 | 패키지 | 들어가는 것 | 금지 |
|---|---|---|---|
| presentation | `presentation`, `presentation/dto` | `{X}Controller`, `Internal{X}Controller`, `{X}Request`, `{X}Response` | Repository·Entity 직접 사용 |
| application | `application`, `application/dto` | `{X}Service`, `{X}QueryService`, `{X}Command`, `{X}Result`, 외부 호출 포트 인터페이스 | Request/Response DTO, JPA·Feign 구현 직접 의존 |
| domain | `domain/model`, `domain/repository`, `domain/exception` | Entity, enum, Repository 인터페이스, `{Service}ErrorCode` | Spring Data·Feign 의존 |
| infrastructure | `infrastructure/persistence`, `infrastructure/client`, `infrastructure/config` | JPA·QueryDSL 구현, FeignClient, 설정 빈 | 비즈니스 규칙 |

외부 의존(토큰 발급, 다른 서비스 호출, Slack)은 **application에 인터페이스, infrastructure에 구현** — 예: `application/TokenProvider` ← `infrastructure/jwt/JwtTokenProvider`.

## common 모듈 — 다시 만들지 않는다
| 필요한 것 | 쓸 것 |
|---|---|
| Audit 컬럼·논리 삭제 | `BaseEntity` 상속, `softDelete(username)` |
| 성공 응답 | `ApiResponse.ok(...)`, `ApiResponse.created(...)` |
| 목록 응답 | `PageResponse.from(page, XResponse::from)` |
| 예외 | `BusinessException(XxxErrorCode)`, 공통은 `CommonErrorCode` |
| 현재 사용자 | `@CurrentUser UserContext user` |
| 역할 확인 | `@RequireRole(UserRole.MASTER)` |
| 소유자 확인 | `user.validateSelfOrMaster(ownerId)`, `user.validateMaster()` |
| 페이지·정렬 | `PageRequests.normalize(pageable)`, `QuerydslSorts.toOrderSpecifiers(...)` |
| Feign 헤더 전달·에러 변환 | 자동 (`FeignConfig`) — 직접 구현 금지 |

common에는 도메인 로직·서비스별 DTO를 넣지 않는다. common을 바꾸면 `doc/design/02-architecture.md` §3과 이 파일을 함께 고친다.

## 예외
- 던지는 예외는 `BusinessException` 하나. `RuntimeException`, `IllegalArgumentException`, `EntityNotFoundException`을 직접 던지지 않는다
- 에러 코드는 서비스별 enum `{Service}ErrorCode implements ErrorCode` (`domain/exception`). 응답 `code` = enum 상수 이름
- 이름: `{리소스}_{상황}` — `HUB_NOT_FOUND`, `STOCK_INSUFFICIENT`, `DUPLICATE_USERNAME`
- 예외를 잡아 로그만 찍고 삼키지 않는다

## 네이밍
| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | 역할 접미사 | `HubController`, `HubService`, `HubQueryService`, `HubRepository`, `HubJpaRepository`, `HubRepositoryImpl`, `HubClient` |
| DTO | 계층별 접미사, `record` | `CreateHubRequest` → `CreateHubCommand` → `HubResult` → `HubResponse` |
| 메서드 | 동사로 시작, 조회 `get`(없으면 예외)/`find`(Optional)/`search`(목록) | `getHub`, `findByName`, `searchHubs` |
| 상수 | UPPER_SNAKE | `SORTABLE_FIELDS` |

## Lombok · 스타일
- 허용: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Slf4j`, `@Builder`(생성자에만)
- 금지: `@Data`, Entity의 `@Setter`, `@AllArgsConstructor`(Entity)
- 의존성 주입은 생성자 주입(`private final` + `@RequiredArgsConstructor`). 필드 `@Autowired` 금지
- 주석·Javadoc은 한국어. API·테이블은 SA 번호를 붙인다 (`/** H-03 허브 수정 */`, `(SA 5.3)`)
- 시간은 `LocalDateTime` (DB `TIMESTAMP`, 타임존 Asia/Seoul)

## 문서 동기화
코드를 바꾸면 같은 작업에서 문서도 바꾼다 — 매핑은 루트 `CLAUDE.md`·`doc/README.md`, 점검은 `script/docs-check.sh`.
