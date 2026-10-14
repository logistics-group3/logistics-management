# 스텝 28. 공용 스킬 재구성과 도구 연결

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 4.3과 6장 결정(공통·도메인 스킬 분류, 변경은 머지 때 판단, learn-explain 제거, AI가 한 일은 알아서 기록, "거절한 제안 반복 금지" 삭제)
- 결과: Claude Code(`claude -p`)와 Codex(`codex exec`, 0.160.0) 양쪽에서 스킬 6개가 보이는 것을 확인했다. Claude Code는 presentation 파일을 열면 `presentation 규칙`과 `user-service 지침`을 불러오고, domain 파일을 열면 `domain 규칙`만 불러왔다(presentation 규칙은 불러오지 않음)

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `.agents/skills/impl-service` | KMH 원본. 참조 문서를 SA, 모듈 `AGENTS.md`, 서비스 협업 창구로 바꾸고, 구현 노트를 `docs/services/{service}/notes.md`에 남긴다 |
| `.agents/skills/learn-review` | KMH 원본. 기준을 리뷰 체크리스트와 레이어 규칙으로 바꿨다 |
| `.agents/skills/learning-note` | junhyun 질문 노트에 KMH `learn-note`의 하루 정리(`daily/`)를 합쳤다. 경로는 `docs/learning/<이름>/` |
| `.agents/skills/troubleshooting` | 새 스킬. KMH `learn-note`의 트러블슈팅 부분을 떼어 TS 형식으로 `docs/troubleshooting.md`에 남긴다 |
| `.agents/skills/adr` | 서비스 결정은 `docs/services/{service}/decisions/`에 둔다 |
| `.agents/skills/README.md` | 스킬 목록, 공통·도메인 분류, 이름 규칙, PR 리뷰 규칙 |
| `script/setup-agents.sh` | `.agents/skills/*`를 `.claude/skills/`에, `docs/agents/rules/*.md`를 `.claude/rules/`에 링크한다. 링크를 못 만들면 복사한다 |
| `.gitignore` | `.claude/skills/`, `.claude/rules/`는 스크립트 결과물이라 올리지 않는다 |
| `AGENTS.md` | 스킬 표, 기록·제안 규칙, 개인 지침, 검증 명령 |
| `CLAUDE.md` | Claude 전용 안내(스킬·규칙 연결, 모듈 CLAUDE.md) |

learn-explain은 팀 의견(권민혁)에 따라 넣지 않았다.

## 리뷰 포인트
1. **묶음 폴더를 만들지 않은 이유**: Notion에서는 공통·도메인 폴더로 나누자고 했다. 공식 문서를 확인하니 Claude Code와 Codex 모두 `skills/<이름>/SKILL.md` 형태만 안내하고, 묶음 폴더 안의 스킬을 찾는지는 문서에 없다. 그래서 폴더는 평평하게 두고 분류는 README 표와 이름 규칙(`{service}-{작업}`)으로 나타낸다.
2. **정본은 `.agents/skills`**: Codex는 저장소의 `.agents/skills`를 직접 읽는다(공식 문서, 실제 확인). Claude Code는 `.claude/skills`만 읽으므로 링크로 연결한다. 링크된 스킬 폴더를 따라가는 것은 공식 문서에 있다.
3. **레이어 규칙도 같은 방식**: 정본 `docs/agents/rules/*.md` 머리말에 `paths:`를 두고 `.claude/rules/`에 링크했다. `.claude/rules` 안에서 `@` 가져오기가 되는지는 문서에 없어서, 문서에 있는 링크 방식을 썼다.
4. **링크를 Git에 올리지 않는 이유**: Windows 기본 설정에서 Git 심볼릭 링크는 경로가 적힌 일반 파일로 체크아웃된다. 각자 스크립트를 실행해 만들고, 링크가 안 되면 복사한다.
5. **기록은 알아서, 제안은 사람이 한 일에만**: 에이전트가 한 일(구현, 문제 해결, 질문 답, 결정)은 동의 없이 기록하고 경로를 알린다. 사람은 PR에서 검토한다. 사람이 직접 한 일(직접 쓴 코드, 하루 마무리)에는 스킬을 제안한다.

## 팀원이 할 일
- clone한 뒤 `script/setup-agents.sh`를 한 번 실행한다. 스킬이나 규칙 파일이 추가되면 다시 실행한다.
