@AGENTS.md

# Claude Code 전용

규칙의 정본은 `AGENTS.md`, `docs/agents/`, 모듈별 `{module}/AGENTS.md`다. 이 파일에는 Claude Code에만 있는 기능을 적는다.

`@AGENTS.md`의 프런트 검증 규칙도 적용한다. `./gradlew build`만으로 전체 빌드 완료라고 보고하지 않는다. 프런트가 있는 이 저장소의 명령·실행 조건은 [전체 빌드·검증](docs/guides/full-build.md)에 있다.

- 공용 스킬과 레이어 규칙은 `script/setup-agents.sh`를 실행하면 `.claude/skills`, `.claude/rules`에 연결된다. pull로 스킬·규칙이 바뀌면 다시 실행한다. 스크립트는 자기가 만든 항목만 지우므로 같은 폴더에 둔 개인 스킬·규칙은 남는다. 이름이 겹치면 공용 항목을 건너뛰고 알린다. 레이어 규칙은 파일 머리말의 `paths:`에 맞는 파일을 열 때 자동으로 불러온다. 연결하지 않았으면 `.agents/skills/<이름>/SKILL.md`와 `docs/agents/rules/`를 직접 읽는다.
- 모듈 폴더의 `CLAUDE.md`는 `@AGENTS.md` 한 줄이다. 그 폴더의 파일을 다룰 때 모듈 지침을 함께 불러온다.
- 학습 패널, 외부 플러그인은 개인이 선택해서 설치하는 도구이며 공통 실행·검증의 전제가 아니다. 개인 설정은 `CLAUDE.local.md`나 `~/.claude/`에 둔다(`AGENTS.md` "개인 지침").
- 문서 동기화 검사를 응답이 끝날 때마다 돌리고 싶으면 개인 설정 `.claude/settings.local.json`에 종료 훅을 건다. 누락이 있으면 `exit 2`로 Claude에게 알려 문서를 고치게 한다. 팀 공통 설정이 아니며, 훅 연결 자체는 이 저장소에서 실행해 확인하지 않았다(스크립트의 `--hook` 동작만 확인).
  ```json
  {"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "\"$CLAUDE_PROJECT_DIR\"/script/docs-check.sh --hook"}]}]}}
  ```
