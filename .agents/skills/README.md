# 공용 스킬

Claude Code와 Codex가 함께 쓰는 스킬의 정본이다. 스킬마다 `<이름>/SKILL.md` 하나다. 언제 쓰고 무엇을 기록하는지는 루트 `AGENTS.md`의 "공용 스킬" 절에 있다.

| 스킬 | 분류 | 하는 일 |
|---|---|---|
| [impl-service](impl-service/SKILL.md) | 공통 | 서비스 기능을 계층별로 구현하고 구현 노트를 남긴다 |
| [learn-review](learn-review/SKILL.md) | 공통 | 직접 쓴 코드를 힌트부터 단계적으로 리뷰한다 |
| [learning-note](learning-note/SKILL.md) | 공통 | 질문 답과 하루 학습을 개인 학습 노트로 남긴다 |
| [troubleshooting](troubleshooting/SKILL.md) | 공통 | 해결한 문제를 문제 해결 기록에 남긴다 |
| [adr](adr/SKILL.md) | 공통 | 되돌리기 어려운 결정을 기록한다 |
| [handoff](handoff/SKILL.md) | 공통 | 브랜치 작업 인계 파일을 갱신한다 |

## 규칙
- **분류**: 모든 서비스에서 쓰는 스킬은 "공통", 한 서비스에서만 쓰는 스킬은 "도메인"이다. 도메인 스킬은 이름을 `{service}-{작업}`으로 짓는다(예: `order-saga-check`). 위 표의 분류 열에 적는다.
- **폴더는 평평하게 둔다**: `common/`, `domain/` 같은 묶음 폴더를 만들지 않는다. Claude Code와 Codex 모두 `skills/<이름>/SKILL.md` 형태만 안내한다.
- **머리말**: `name`(폴더 이름과 같게)과 `description`(언제 쓰는지, 예시 요청 포함)을 쓴다. 도구 전용 필드(`argument-hint` 등)는 써도 되지만 본문 절차는 어느 도구에서도 읽히게 쓴다.
- **추가·수정·삭제는 PR에서 판단한다.** PR 본문에 이유를 쓴다.
- **연결**: Codex는 이 폴더를 직접 읽는다. Claude Code는 `script/setup-agents.sh`를 실행하면 `.claude/skills/`에서 찾는다. 스킬을 추가·수정·삭제한 변경을 pull하면 다시 실행한다.
