# AGENTS.md

물류 관리 MSA 부트캠프 프로젝트. 이 파일은 모든 에이전트(Claude Code, Codex 등)의 공통 진입점이며 대전제만 둔다. 세부 규칙은 아래 "작업별 필수 문서"에 있다.

## 프로젝트
- Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.x, Gradle 8.14 멀티 모듈, PostgreSQL 16
- `frontend/`: React·TypeScript·Vite, Node.js 24·npm. 별도 npm 프로젝트이며 Gradle 빌드에 포함되지 않는다.
- 모듈과 포트: `config-server`(8888), `eureka-server`(8761), `gateway-service`(8080), `{user,hub,company,order,delivery}-service`(8081~8085), `common-core`(웹 무관 공통 계약: 에러 코드·응답, 사용자 헤더), `common`(MVC 서비스 공통, common-core 포함)
- 설정: 서비스 설정은 `config-repo/`(config-server가 제공)에 둔다. 각 모듈의 `application.yml`에는 이름과 `spring.config.import`만 둔다.
- 패키지: `com.sparta.logistics.{서비스}`, 내부는 `presentation / application / domain / infrastructure`
- 설계 정본: `docs/SA/sparta-logistics-sa.md`. 설계와 다르게 구현해야 하면 먼저 사용자에게 확인하고 `docs/decisions/`에 기록한다.
- 팀 공유 저장소다. 팀 합의(SA 문서, 결정 기록)를 개인 판단으로 덮어쓰지 않는다.

## 대전제
1. **완성과 학습을 함께 추구한다.** 사용자는 백엔드 취업을 준비 중이다. 기초부터 아키텍처, 운영까지 이해할 수 있게 "무엇을"과 함께 "왜"를 남긴다.
2. **질문은 기록한다.** 사용자가 지식이나 방법을 물으면 답한 뒤 `docs/learning/<이름>/qna/`에 날짜와 함께 저장한다(`<이름>`은 팀원 영문 이름: haneol, junhyun, kej, kmh, seungkeun).
3. **코드는 간결하되 수준 높게 쓴다.** 학습 가치가 큰 지점에만 `Note: ` 주석을 단다.
4. **품질은 작업량과 무관하게 유지한다.** 검증 근거 없이 완료라고 말하지 않는다.
5. **구현 주체는 작업마다 다르다.** 기본은 에이전트가 구현하고, 사용자가 "내가 할게"라고 하면 단계 안내와 리뷰만 한다.
6. **작업 상태를 남긴다.** 작업 단계가 바뀌면 현재 브랜치의 progress 파일을 갱신한다. 다음 에이전트는 그 파일에서 이어간다.
7. **결정은 이유와 함께 남긴다.** 버전, 구조, 라이브러리처럼 되돌리기 어려운 선택은 `docs/decisions/`에 기록한다.
8. **톤**: 코드 주석, 문서, 커밋은 한국어 평서체로 쓴다. AI 말투(과한 공감, 감탄, 장황한 서두)와 이모지를 쓰지 않는다.
9. **비밀값 금지**: 토큰, 실제 비밀번호, `.env`는 커밋하지 않는다.

## 시작 절차
1. `git branch --show-current`로 브랜치를 확인하고 `docs/progress/<브랜치명의 / 를 - 로 바꾼 이름>.md`를 읽는다. 없으면 `handoff` 스킬 형식으로 만든다.
2. 고칠 모듈이 정해지면 그 모듈 폴더의 `AGENTS.md`(예: `order-service/AGENTS.md`)를 먼저 읽는다. 하위 폴더의 지침을 자동으로 불러오지 못하는 에이전트도 있으므로 직접 읽는다.
3. 작업 유형에 맞는 아래 문서를 읽고 시작한다. 문서를 어디에 둘지 모르면 `docs/README.md`(문서 지도)를 본다.

## 작업별 필수 문서
| 언제 | 읽을 문서 |
|---|---|
| 코드 작성·수정 전 | `docs/agents/coding-conventions.md`, 고칠 모듈의 `{module}/AGENTS.md` |
| 계층별 코드(presentation, application, domain, infrastructure, 마이그레이션, 설정, 테스트) | `docs/agents/rules/` (경로별 파일은 `rules/README.md`) |
| 다른 서비스 API를 호출하거나 내 API 계약을 바꿀 때 | `docs/services/{service}/README.md` |
| 에러 코드를 추가·변경할 때 | `docs/error-codes.md` |
| 사용자 질문에 답할 때, 개념 설명·"직접 해보기" 작성 시 | `docs/agents/learning-guide.md` |
| 작업 완료 보고·리뷰·테스트 전 | `docs/agents/quality-gates.md`, `docs/guides/review-checklist.md` |
| 프런트 작업·프런트가 쓰는 API 계약 변경·전체 빌드 요청 | `frontend/AGENTS.md`, `docs/guides/full-build.md` |
| 문서·노트·커밋·PR 본문을 쓸 때 | `docs/guides/writing.md` |
| 브랜치·커밋·PR 작업 시 | `docs/guides/git-workflow.md` |
| 결정 기록 시 | `docs/decisions/README.md` |

## 공용 스킬 (`.agents/skills/`)
| 스킬 | 언제 | 기록 위치 |
|---|---|---|
| `impl-service` | 서비스 기능(API, 엔티티, FeignClient)을 구현할 때 | `docs/services/{service}/notes.md`, 협업 창구 구현 현황 |
| `learn-review` | 사용자가 직접 쓴 코드를 리뷰해 달라고 할 때 | 대화만 |
| `learning-note` | 개념·방법 질문에 답했을 때. 하루 정리는 제안한 뒤 사용자가 원할 때만 | `docs/learning/<이름>/` |
| `troubleshooting` | 막혔던 문제를 해결했을 때 | `docs/troubleshooting.md` |
| `adr` | 되돌리기 어려운 결정을 했을 때 | `docs/decisions/`, `docs/services/{service}/decisions/` |
| `handoff` | 작업을 시작·전환·종료할 때 | `docs/progress/<branch>.md` |

- **에이전트가 한 일은 알아서 기록한다.** 위 표의 상황이 되면 동의를 기다리지 않고 해당 위치에 쓰고, 답 끝에 `기록: <경로>`를 남긴다. 하루 정리는 사용자가 한 일을 정리하는 것이라 아래 제안 규칙을 따른다. 사람은 PR에서 기록을 검토하고 고친다.
- **사람이 직접 한 일은 스킬을 제안한다.** 사용자가 직접 쓴 코드를 커밋하려 하면 `learn-review`, "오늘은 여기까지"라고 하면 `learning-note` 하루 정리를 답 끝에 한 줄로 제안한다. 한 번 거절한 제안도 다음 상황에서 다시 제안할 수 있다.
- 스킬 목록과 분류(공통, 도메인)는 `.agents/skills/README.md`에 있다. 스킬 추가·수정·삭제는 PR에서 판단한다.
- Codex는 `.agents/skills`를 직접 읽는다. Claude Code는 `script/setup-agents.sh`를 실행하면 `.claude/skills`에서 찾는다. pull로 스킬이나 규칙이 바뀌면 다시 실행한다. 스킬을 자동으로 불러오지 못하는 에이전트는 해당 `SKILL.md`를 직접 읽고 따른다.

## 개인 지침
팀 규칙이 맞지 않는 부분은 저장소를 고치지 않고 개인 지침으로 보탠다. 팀 규칙과 충돌하면 팀 규칙이 우선이다.
- Claude Code: 루트 `CLAUDE.local.md`(이미 `.gitignore`에 있다) 또는 `~/.claude/CLAUDE.md`. worktree를 여러 개 쓰면 `~/.claude/`의 파일을 `CLAUDE.local.md`에서 `@~/.claude/my-logistics.md`처럼 가져온다.
- Codex: `~/.codex/AGENTS.md`. 저장소 안에 `AGENTS.override.md`를 두면 그 폴더의 팀 `AGENTS.md`를 대신 읽으므로 쓰지 않는다.
- 그 밖에 저장소에 올리지 않을 개인 파일은 `.git/info/exclude`에 경로를 적는다(나에게만 적용되는 `.gitignore`).

## 검증 명령
- 백엔드 빌드·테스트: `./gradlew build`. 프런트 검증은 포함하지 않는다.
- 프런트 최초 준비 또는 lockfile 변경: `npm --prefix frontend ci`
- 프런트 검증: `npm --prefix frontend run build`, `npm --prefix frontend run lint`, `npm --prefix frontend run format:check`, `npm --prefix frontend run test`
- 전체 빌드 요청이면 백엔드와 프런트 명령을 모두 실행하고 결과를 각각 보고한다. 프런트 코드·의존성 또는 프런트가 쓰는 API의 요청/응답·권한을 바꾸면 프런트 검증도 한다. 명령·실행 조건의 정본은 [전체 빌드·검증](docs/guides/full-build.md)이다.
- 브라우저 E2E는 `npm --prefix frontend run test:e2e`. 실행 중인 전용 테스트 서버·UI·시드와 Slack 비활성화가 필요하며, 빌드·단위 테스트와 별도 결과로 보고한다.
- 단일 모듈 실행: `./gradlew :eureka-server:bootRun` (서비스는 config-server를 먼저 띄운다)
- 전체 실행: `script/run.sh` (`.env`가 없으면 만든다), 완료 기준 확인까지: `script/run.sh --check`
- 에러 코드 중복·문서 누락: `script/error-codes-check.sh`, 문서 동기화: `script/docs-check.sh`
- Claude Code 스킬·규칙 연결: `script/setup-agents.sh` (clone 후, pull로 스킬·규칙이 바뀌었을 때)
- 셸 기본 JDK가 21이어도 Gradle toolchain이 17로 컴파일·실행한다. JAVA_HOME을 바꾸지 않는다.
