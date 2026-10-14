# common agent.md

> 이 모듈의 **협업 창구**다. 사람·AI 모두 `common` 또는 루트 공유 파일을 수정하기 전에 루트 `CLAUDE.md`와 이 파일을 먼저 읽는다.
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
- 도메인 로직(특정 서비스에만 필요한 코드)은 넣지 않는다. "모든 서비스가 같이 쓰고, 바꾸면 전부 고쳐야 하는 것"만 넣는다.
- 기존 public API(클래스·메서드 시그니처, 응답 필드)는 **삭제·이름 변경 대신 추가**로 확장한다. 불가피하면 로그에 `OPEN`으로 올려 합의 후 진행.
- 라이브러리 버전은 루트 `build.gradle`의 `ext`에서만 관리한다.
- 에러 코드: 공통 코드만 `CommonErrorCode`에 둔다. 도메인 코드는 각 서비스 enum에 둔다.

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
