# AGENTS.md

물류 관리 MSA 부트캠프 프로젝트. 이 파일은 모든 에이전트(Claude Code, Codex 등)의 공통 진입점이며 대전제만 둔다. 세부 규칙은 아래 "작업별 필수 문서"에 있다.

## 프로젝트
- Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.x, Gradle 8.14 멀티 모듈, PostgreSQL 16
- 모듈: `common`, `eureka-server`, `gateway-service`, `{user,hub,company,order,delivery}-service`
- 패키지: `com.sparta.logistics.{서비스}`, 내부는 `presentation / application / domain / infrastructure`
- 설계 정본: `docs/SA/Sparta_logistics_SA_Master .md`. 설계와 다르게 구현해야 하면 먼저 사용자에게 확인하고 `docs/decisions/`에 기록한다.
- 팀 공유 저장소다. 팀 합의(SA 문서, 결정 기록)를 개인 판단으로 덮어쓰지 않는다.

## 대전제
1. **완성과 학습을 함께 추구한다.** 사용자는 백엔드 취업을 준비 중이다. 기초부터 아키텍처, 운영까지 이해할 수 있게 "무엇을"과 함께 "왜"를 남긴다.
2. **질문은 기록한다.** 사용자가 지식이나 방법을 물으면 답한 뒤 `docs/learning/qna/`에 날짜와 함께 저장한다.
3. **코드는 간결하되 수준 높게 쓴다.** 학습 가치가 큰 지점에만 `Note: ` 주석을 단다.
4. **품질은 작업량과 무관하게 유지한다.** 검증 근거 없이 완료라고 말하지 않는다.
5. **구현 주체는 작업마다 다르다.** 기본은 에이전트가 구현하고, 사용자가 "내가 할게"라고 하면 단계 안내와 리뷰만 한다.
6. **작업 상태를 남긴다.** 작업 단계가 바뀌면 현재 브랜치의 progress 파일을 갱신한다. 다음 에이전트는 그 파일에서 이어간다.
7. **결정은 이유와 함께 남긴다.** 버전, 구조, 라이브러리처럼 되돌리기 어려운 선택은 `docs/decisions/`에 기록한다.
8. **톤**: 코드 주석, 문서, 커밋은 한국어 평서체로 쓴다. AI 말투(과한 공감, 감탄, 장황한 서두)와 이모지를 쓰지 않는다.
9. **비밀값 금지**: 토큰, 실제 비밀번호, `.env`는 커밋하지 않는다.

## 시작 절차
1. `git branch --show-current`로 브랜치를 확인하고 `docs/progress/<브랜치명의 / 를 - 로 바꾼 이름>.md`를 읽는다. 없으면 `handoff` 스킬 형식으로 만든다.
2. 작업 유형에 맞는 아래 문서를 읽고 시작한다.

## 작업별 필수 문서
| 언제 | 읽을 문서 |
|---|---|
| 코드 작성·수정 전 | `docs/agents/coding-conventions.md` |
| 사용자 질문에 답할 때, 개념 설명·"직접 해보기" 작성 시 | `docs/agents/learning-guide.md` |
| 작업 완료 보고·리뷰·테스트 전 | `docs/agents/quality-gates.md` |
| 브랜치·커밋·PR 작업 시 | `docs/agents/git-workflow.md` |
| 결정 기록 시 | `docs/decisions/README.md` |

## 공용 스킬 (`.agents/skills/`)
- `learning-note`: 질문과 답을 학습 노트로 저장
- `handoff`: 브랜치 progress 파일 갱신
- `adr`: 결정 기록 작성

스킬을 자동으로 불러오지 못하는 에이전트는 해당 `SKILL.md`를 직접 읽고 따른다.

## 검증 명령
- 전체 빌드: `./gradlew build`
- 단일 모듈 실행: `./gradlew :eureka-server:bootRun`
- 셸 기본 JDK가 21이어도 Gradle toolchain이 17로 컴파일·실행한다. JAVA_HOME을 바꾸지 않는다.
