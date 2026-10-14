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

### COMMON-007 문서 구조 정리 (루트 README, Docs 재구성)
- 상태: DONE
- 작성: 권민혁 / 2026-10-14
- 관련: `README.md`, `Docs/README.md`, `Docs/07-트러블슈팅.md`, `Docs/team/`, `CLAUDE.md` §8-2
- 내용: 루트 `README.md`가 비어 있었고, 실행 방법·열린 논의 목록이 여러 문서에 사본으로 있어 낡을 위험이 있었다. 설계 문서(01~05)에 날짜가 박힌 구현 현황이 섞여 있었다.
- 결정: 루트 `README.md`(소개·아키텍처·빠른 실행·문서 지도·팀)를 작성했다. `Docs/README.md`에 "정보별 기준 위치 한 곳" 갱신 규칙을 두고, 열린 논의 사본 목록(06, 팀 공유 개요)은 `agent.md` 조회 명령으로 대체했다. 트러블슈팅 기록용 `Docs/07-트러블슈팅.md`를 신설해 기존 버그 기록을 TS-001~005로 옮겼다. 팀 공유 개요는 `Docs/team/프로젝트-개요.md`로 이동했다. `Docs` 폴더명(소문자 `docs` 여부)은 리뷰 후 채택 브랜치에 맞춰 결정한다.

### COMMON-008 AI 학습·구현 skill 추가 (`.claude/skills/`)
- 상태: DONE
- 작성: 권민혁 / 2026-10-14
- 관련: `.claude/skills/{impl-service,learn-explain,learn-review,learn-note}`, `Docs/team/구현-노트/`, `Docs/team/학습-노트/`
- 내용: 학습이 주 목적인 프로젝트에서 AI가 구현을 맡으면 무엇을 왜 만들었는지 남지 않는 문제가 있다. 팀원 모두가 같은 절차로 AI를 쓰도록 skill을 공유한다.
- 결정: 분류는 접두어로 나눈다 — 학습 `learn-*`(explain·review·note), 구현 `impl-*`(service). `impl-service`는 계층별로 멈추고 설명하며(지시자가 요청하면 주제 단위로 설명 생략), 마지막에 처리 과정·핵심을 `Docs/team/구현-노트/{서비스}.md`에 남긴다. 다른 서비스 계약 요청은 별도 skill 없이 `impl-service` 1단계에 포함했다. skill에는 규칙 본문을 복사하지 않고 루트·모듈 `CLAUDE.md`를 가리킨다.

### COMMON-009 skill 사용 가이드 `Docs/skills/` 추가
- 상태: DONE
- 작성: 권민혁 / 2026-10-14
- 관련: `Docs/skills/`, `.claude/skills/`, COMMON-008
- 내용: `SKILL.md`는 AI용 절차라 팀원이 읽고 사용법을 익히기 어렵다.
- 결정: `Docs/skills/`에 사람용 사용 설명서(개요 README + skill별 문서: 목적·호출 예시·진행 흐름·결과물·팁)를 둔다. 절차 원본은 `SKILL.md`이며 어긋나면 `SKILL.md`가 우선한다. `SKILL.md`의 동작을 바꾸면 설명서도 함께 고친다.

### COMMON-010 skill을 상황에 맞게 AI가 먼저 제안
- 상태: DONE
- 작성: 권민혁 / 2026-10-14
- 관련: 루트 `CLAUDE.md` "학습·구현 skill 제안", `.claude/skills/learn-note/SKILL.md`, `Docs/skills/`
- 내용: `learn-note`가 직접 호출 전용이라 정리 시점을 놓치기 쉽고, skill마다 언제 쓸지 지시자가 기억해야 했다.
- 결정: 실행은 동의 후, 제안은 자동으로 한다. 제안 시점(커밋·마무리 발언, 문제 해결, 직접 짠 코드 커밋 전, impl-service 절차 밖 구현, 설명 후 혼란)을 항상 읽히는 루트 `CLAUDE.md`에 두었다. 한 시점에 한 번 제안하고, 거절한 제안은 반복하지 않으며, "제안 그만"이면 그 대화에서 멈춘다. `learn-note`는 `disable-model-invocation`을 해제해 "응"으로 바로 실행되게 하되, AI 제안으로 시작하면 정리할 항목을 먼저 보여 주고 동의를 받은 뒤에 기록한다. hook 방식은 소음·전원 영향 때문에 채택하지 않았다.
