# 0002. 에이전트 중립 작업 규칙 구조

- 상태: 채택
- 날짜: 2026-10-13
- 결정자: junhyun

## 맥락
- Claude Code와 Codex 등 여러 에이전트가 번갈아 작업한다. 어떤 에이전트가 붙어도 같은 규칙과 맥락으로 이어가야 한다.
- 프로젝트 목표에 구현뿐 아니라 학습(질문 기록, 학습용 주석, 직접 해보기)이 포함된다.

## 결정
- 공통 진입점은 `AGENTS.md`이고 대전제만 둔다. 세부 규칙은 `docs/agents/`에 두고, AGENTS.md에 "언제 무엇을 읽는지"를 명시한다.
- `CLAUDE.md`는 `@AGENTS.md` 임포트와 Claude 전용 기능만 둔다.
- 공용 스킬의 정본은 `.agents/skills/`에 둔다. Claude Code는 `.claude/skills/`의 심볼릭 링크로 읽는다.
- 작업 상태는 브랜치별 `docs/progress/<branch>.md`, 학습 기록은 `docs/learning/`, 결정은 `docs/decisions/`에 텍스트로 남긴다.
- Claude 전용 학습 패널(study-mode)은 위 파일을 보여 주는 계층이며 정본이 아니다.

## 검토한 대안
- CLAUDE.md 중심 구성: Codex가 읽지 않는다.
- 공용 progress 파일 하나: 팀원 브랜치끼리 충돌한다.

## 결과
- 규칙이 바뀌면 AGENTS.md나 docs/agents 한 곳만 고친다.
- Codex에는 패널이 없으므로 노트를 저장할 때 채팅에 경로를 남기는 방식으로 대신한다.
