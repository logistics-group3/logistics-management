@AGENTS.md

# Claude Code 전용

규칙의 정본은 `AGENTS.md`, `docs/agents/`, 모듈별 `{module}/AGENTS.md`다. 이 파일에는 Claude Code에만 있는 기능을 적는다.

- 공용 스킬과 레이어 규칙은 `script/setup-agents.sh`를 실행하면 `.claude/skills`, `.claude/rules`에 연결된다. 레이어 규칙은 파일 머리말의 `paths:`에 맞는 파일을 열 때 자동으로 불러온다. 연결하지 않았으면 `.agents/skills/<이름>/SKILL.md`와 `docs/agents/rules/`를 직접 읽는다.
- 모듈 폴더의 `CLAUDE.md`는 `@AGENTS.md` 한 줄이다. 그 폴더의 파일을 다룰 때 모듈 지침을 함께 불러온다.
- 학습 패널, 외부 플러그인은 개인이 선택해서 설치하는 도구이며 공통 실행·검증의 전제가 아니다. 개인 설정은 `CLAUDE.local.md`나 `~/.claude/`에 둔다(`AGENTS.md` "개인 지침").
