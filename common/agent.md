# common agent.md

> 이 모듈의 **협업 창구**다. 사람·AI 모두 `common` 또는 루트 공유 파일을 수정하기 전에 루트 `CLAUDE.md`, 이 폴더의 `CLAUDE.md`(세부 규칙), 이 파일을 먼저 읽는다.
> **이 모듈의 변경은 비즈니스 서비스 5개 전부에 영향을 준다.** 변경 전 반드시 §5 로그에 기록하고 팀에 공유한다.

## 1. 모듈 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정 — 공통 인프라 채택안 작성자) |
| 성격 | 비즈니스 서비스 공통 라이브러리 (`CommonAutoConfiguration`으로 자동 등록) |
| 함께 관리하는 루트 공유 파일 | `build.gradle`, `settings.gradle`, `docker-compose.yml`, `Dockerfile`, `.dockerignore`, `.env.example`, `infra/postgres/init.sh`, `script/` |
| 사용하지 않는 모듈 | `gateway-service`(WebFlux), `eureka-server` |

## 2. 제공 기능 (서비스가 의존하는 계약)
| 패키지 | 구성 | 바꾸면 영향 |
| --- | --- | --- |
| `response` | `ApiResponse`, `PageResponse`, `Pageables` | 모든 외부 API 응답 형식 |
| `exception` | `ErrorCode`, `CommonErrorCode`, `BusinessException`, `ErrorResponse`, `RemoteErrorCode`, `GlobalExceptionHandler` | 에러 응답 형식, Feign 에러 해석 |
| `context` | `@CurrentUser`, `UserContext`, `UserRole`, `UserHeaders`, `@MasterOnly` | 인증 헤더 계약(Gateway와 일치해야 함) |
| `jpa` | `BaseEntity`, `HeaderAuditorAware` | 모든 테이블의 Audit 컬럼 |
| `feign` | `FeignHeaderInterceptor`, `FeignErrorDecoder` | 서비스 간 헤더 전파, 하위 에러 전파 규칙 |
| `config` | `CommonAutoConfiguration` (JPA Auditing, QueryDSL, Swagger, 위 빈 등록) | 전 서비스 기동 |

## 3. 의존
- 없음 (라이브러리). 단, `UserHeaders`의 헤더 이름은 `gateway-service`의 `AuthenticationFilter`와 반드시 같아야 한다.

## 4. 모듈 고유 규칙
- 세부 규칙은 같은 폴더의 `CLAUDE.md`에 있다 (규칙은 그곳 한 곳에만 둔다, COMMON-005).

## 5. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `COMMON-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 모듈에 요청할 때는 **그 모듈의 agent.md**에 작성

```
### COMMON-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### COMMON-001 에러 코드 이름의 서비스 간 중복 방지
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: 전 서비스 `*ErrorCode`
- 내용: `FeignErrorDecoder`가 하위 서비스 에러 코드를 그대로 전달하므로, 서로 다른 서비스가 같은 코드명을 다른 의미로 쓰면 혼동된다. 제안: 도메인 코드는 리소스명을 접두어로 쓴다(`HUB_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `STOCK_INSUFFICIENT`). SA 공통 에러 코드 표에 있는 코드는 그대로 사용.
- 결정:

### COMMON-002 부분 유니크 인덱스 작성 방식
- 상태: DECIDED
- 작성: 공통 인프라 / 2026-10-13
- 관련: 전 서비스 `db/indexes.sql`
- 내용: 논리 삭제를 고려한 유니크 제약(`WHERE deleted_at IS NULL`)은 JPA로 만들 수 없다.
- 결정: 각 서비스 `src/main/resources/db/indexes.sql`에 `CREATE UNIQUE INDEX IF NOT EXISTS ...`로 작성하고, user-service의 yml 설정(`defer-datasource-initialization`, `spring.sql.init.*`)을 그대로 따른다.

### COMMON-003 엔티티 Q클래스 생성
- 상태: DONE
- 작성: 공통 인프라 / 2026-10-13
- 관련: `common/build.gradle`
- 내용: 엔티티가 `BaseEntity`를 상속하면 `QBaseEntity`가 없어 컴파일 실패했다.
- 결정: `common`에도 QueryDSL 어노테이션 프로세서를 추가해 해결. 서비스 측 조치 불필요.

### COMMON-004 설계 문서 `Docs/` 디렉터리로 정리
- 상태: DONE
- 작성: 권민혁 / 2026-10-13
- 관련: `Docs/`, `CLAUDE.md`, `프로젝트 개요 및 발표 준비 노트.md`
- 내용: SA 문서를 주제별(개요·ERD·API·인프라 구성도·시스템 구조)로 나눠 `Docs/01~05`에 정리했다. 원본 `SA문서.md`, `공통 인프라 개발 범위.md`는 `Docs/reference/`로 이동했다. 코드·빌드 영향 없음.
- 결정: 최종 근거는 계속 `Docs/reference/SA문서.md`. 어긋나면 SA 문서 우선, SA를 고치면 `Docs/` 정리본도 함께 고친다. `CLAUDE.md` 등의 경로 참조는 새 위치로 갱신 완료.

### COMMON-005 규칙 문서를 루트·모듈 `CLAUDE.md`로 분할
- 상태: DONE
- 작성: 권민혁 / 2026-10-14
- 관련: 루트 `CLAUDE.md`, 각 모듈 `CLAUDE.md`·`agent.md` §4
- 내용: 루트 `CLAUDE.md`(207줄)에 서비스 세부 규칙이 모여 있어 어느 모듈을 작업하든 전부 읽히고, `agent.md` §4와 같은 내용이 중복돼 한쪽만 고치면 어긋날 위험이 있었다.
- 결정: 루트 `CLAUDE.md`는 대전제·필수 규칙·서비스 간 공통 계약(orderId 멱등, 도메인 불변식, JWT 계약, Feign 타임아웃 등)만 둔다. 서비스 세부 규칙(재고·Saga·상태 전이·배정·컬럼 길이 등)은 각 모듈 `CLAUDE.md`로 옮겼다. 모듈 `CLAUDE.md` 첫 줄 `@agent.md`로 agent.md를 함께 불러오며, `agent.md` §4는 모듈 `CLAUDE.md`를 가리키는 안내로 바꿨다. `Docs/`가 참조하는 루트 섹션 번호(§4·§5·§6·§7·§8·§11)는 유지했다. 규칙 내용 자체의 변경은 없다.

### COMMON-006 SA 원본 파일명 변경 (`SA문서.md` → `SA_Document.md`)
- 상태: DONE
- 작성: 권민혁 / 2026-10-14
- 관련: `Docs/reference/SA_Document.md`, 루트·모듈 `CLAUDE.md`, `Docs/`
- 내용: SA 원본 파일명을 영문으로 바꿨다 (내용 변경 없음).
- 결정: 최종 근거는 `Docs/reference/SA_Document.md`. 루트·모듈 `CLAUDE.md`, `Docs/`, 팀 공유 개요의 경로 참조를 새 이름으로 갱신했다. COMMON-004 등 이전 로그의 옛 경로는 기록으로 남긴다.
