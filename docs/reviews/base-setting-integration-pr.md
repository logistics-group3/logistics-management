# PR 제목

feat(infra): 공통 인프라 통합과 문서·AI 지침 정비 — 팀 검토 의견 반영

## 상황 (Situation)
다섯 명이 같은 공통 인프라 범위를 각자 브랜치에서 구현했습니다. 제 브랜치(junhyun)를 기반으로 다른 분들 브랜치에서 좋은 부분을 골라 합친 통합 후보(`81780df`)를 만들었습니다. 이 커밋에는 한얼님의 사용자 헤더 검증, 은지님의 반복 삭제 방어, 민혁님의 DB 계정 초기화, 승근님의 public 스키마 차단이 들어가 있습니다.

이후 Notion 문서 세 개에 다들 의견을 남겨 주셔서, 그 내용을 이 PR에 반영했습니다.
- [main 통합 방안](https://app.notion.com/p/3f97bd823b758089bac4fc833688aa89)
- [main 통합 방안 — 팀 합의 대조·추가 검토](https://app.notion.com/p/3f97bd823b7581feb841e0c0da37eced)
- [docs·AI 지침 통합 방안](https://app.notion.com/p/3f97bd823b7581c0a9d1cf1da4b14362)

## 과제 (Task)
- Notion에서 정한 내용을 코드와 문서에 반영해 main의 첫 기준선으로 올립니다. 「공통 인프라 개발 범위」의 완료 기준 6개는 그대로 지킵니다.
- 원래는 문서·AI 지침을 dev 대상 PR로 따로 올리기로 했습니다. 그런데 dev에서 다 같이 같은 규칙으로 바로 시작하는 편이 나아서 이 PR에 같이 넣었습니다. 커밋을 스텝별로 나눠 두었으니 문서 쪽만 보실 분은 `575a411` 이후 커밋만 보시면 됩니다.
- 도메인 기능, 실패 경로 E2E, 설정 자동 refresh는 이번 범위에서 뺐습니다.

## 행동 (Action)
### 코드 (스텝 13~21)
- 실행 환경: 줄바꿈 LF 고정, UTF-8 컴파일, Gradle 데몬 JDK 17, 비밀값 무시 패턴 추가 (은지님, 민혁님, 승근님, 한얼님 의견)
- Eureka 로컬 튜닝: 자기 보호를 끄고 갱신 5초 / 만료 15초 / 목록 조회 5초로 줄였습니다(민혁님 값). 응답 캐시도 꺼서 강제 종료한 서비스가 17초 안에 목록에서 빠집니다.
- `UserContext`에 소유자 검증 헬퍼(`validateSelfOrMaster` 등)를 추가했습니다 (한얼님)
- common 공통 빈을 조건부 자동 설정 6개로 바꿨습니다 (은지님, 민혁님, 승근님 방식, ADR 0009)
  - `common.<묶음>.enabled=false`로 묶음별로 끌 수 있습니다.
  - 같은 타입의 빈을 직접 만들면 그쪽이 우선합니다.
  - 서비스 메인 클래스의 `scanBasePackages`는 지웠습니다.
- 정렬 허용 목록 `SortAllowList`와 `QuerydslSupport`를 common에 넣었습니다 (승근님)
- 내부 API(I-01)는 `ApiResponse`로 감싸지 않고 DTO를 그대로 반환합니다 (한얼님, 민혁님, ADR 0010)
- user-service가 뜰 때 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`로 MASTER 계정을 만듭니다 (민혁님, 은지님)
- 에러 코드 이름 규칙, `docs/error-codes.md`, 중복 검사 스크립트를 추가했습니다 (민혁님, 한얼님)

### 문서·AI 지침 (스텝 22~33)
- 문서 지도(`docs/README.md`)와 작성 가이드를 만들었습니다. 서비스끼리 API 계약과 요청을 주고받는 협업 창구는 `docs/services/{service}/`에 있습니다 (민혁님 `agent.md` 구조)
- 모듈마다 `AGENTS.md`/`CLAUDE.md`를 두고, 레이어별 코드 규칙을 `docs/agents/rules/`에 정리했습니다 (한얼님 규칙 기반)
- 승근님 ADR을 공통과 서비스별로 나눠 옮겼습니다. 서비스 ADR 9개는 `제안` 상태라 담당자분이 확정해 주시면 됩니다.
- 공용 스킬 6개를 정리했습니다. Codex는 `.agents/skills`를 바로 읽고, Claude Code는 `script/setup-agents.sh`로 연결합니다.
- 팀 가이드: 구현 순서, 로컬 실행, 설정 보안, Git 흐름, 리뷰 체크리스트, 문제 해결 기록
- `script/run.sh` 하나로 실행합니다. PR마다 문서 동기화(`docs-check`)와 에러 코드를 검사하는 워크플로도 붙였습니다.
- 학습 노트는 `docs/learning/<이름>/`으로 사람별로 나눴습니다.

### 전수검사 후 수정 (스텝 34)
PR을 올린 뒤 전체를 한 번 더 훑어봤고, 병합 전에 고쳐야 할 것 7건을 고쳤습니다.
- `setup-agents.sh`가 `.claude/skills` 안의 개인 파일까지 지우던 문제
- "Config Server 값이 환경 변수보다 우선한다"고 적혀 있었는데, 직접 띄워 보니 반대였습니다. 문서를 고치고 DB URL에 `${DB_PORT:5432}`를 넣었습니다.
- Feign 타임아웃·연결 실패는 `BusinessException`이 아니라 `FeignException`으로 올라옵니다. 보상 로직을 짤 때는 둘 다 잡아야 해서 규칙 문서에 예시를 넣었습니다.
- `db-check.sh`가 127.0.0.1(trust)로 접속하고 있어서 사실 비밀번호를 검사하지 않았습니다. 컨테이너 IP로 접속하게 바꿨습니다.
- 그 밖에 에러 코드 검사 스크립트 보정, ADR 0008 후속 결정 추가, 하루 정리 기록 규칙 정리

나머지(MASTER 비밀번호 검증, Gateway의 I-01 응답 방어 등)는 dev에서 이어서 하려고 `docs/reviews/base-setting-integration-audit.md`에 정리해 두었습니다.

## 결과 (Result)
- `./gradlew clean build` 통과 (테스트 98개, 실패 0)
- `script/run.sh --reset --check`: 새 볼륨에서 E2E 15개 통과
- `script/db-check.sh`: 서비스 계정 5개 통과. 틀린 비밀번호를 넣으면 실패하는 것도 확인했습니다.
- Claude Code와 Codex 둘 다 스킬 6개를 인식합니다.
- GitHub Actions `docs-check`, `error-codes-check` 통과

검증은 기존 로컬 환경과 겹치지 않게 별도 Compose 프로젝트와 다른 포트로 했습니다. Windows에서 clone·실행하는 것과 각자 IDE import는 아직 확인하지 못했습니다. Windows 쓰시는 분은 한 번 돌려 봐 주시면 감사하겠습니다.

## 리뷰 때 봐 주셨으면 하는 부분
1. **common 자동 설정 (ADR 0009)**: 공통 동작을 바꾸고 싶으면 같은 타입 빈을 만드시면 됩니다. 묶음 전체를 끌 때만 `common.<묶음>.enabled=false`를 씁니다.
2. **서비스 ADR 9개** (`docs/services/*/decisions/`): SA와 다른 부분을 적어 두었으니 담당하시는 서비스 것만 확인 부탁드립니다.
3. **문서 동기화 검사 범위** (`docs/doc-map.tsv`): 다른 담당자가 읽는 문서(API 계약, 에러 코드, 실행 안내)만 걸어 두었습니다. 더 넓혀야 할지 의견 주세요.

## 머지 후 할 일
- 처음 실행은 `script/run.sh --reset`으로 DB를 새로 시작해 주세요. 기존 볼륨은 이어 쓰지 않기로 했습니다.
- 기존 `.env`에 `MASTER_USERNAME`, `MASTER_PASSWORD` 두 줄을 추가해 주세요 (`.env.example` 참고).
- Claude Code를 쓰시면 `script/setup-agents.sh`를 한 번 실행해 주세요.
- 앞으로 작업은 main에서 만든 `dev`에서 `feat/{모듈}-{작업}` 브랜치로 합니다 (`docs/guides/git-workflow.md`).
- Windows에서 이미 clone하셨다면 줄바꿈 규칙이 적용되도록 한 번 다시 checkout해 주세요.
- 저장소 관리자분은 `skip-docs` 라벨과 main·dev 보호 규칙 설정을 부탁드립니다.

## 참고
- 스텝별 설명: `docs/learning/junhyun/steps/step-13` ~ `step-34`
- 선별 근거: `docs/reviews/base-setting-integration.md`, ADR 0008
- 전수검사 결과와 후속 작업: `docs/reviews/base-setting-integration-audit.md`
- 결정 기록: `docs/decisions/`, 서비스별 결정은 `docs/services/{service}/decisions/`
