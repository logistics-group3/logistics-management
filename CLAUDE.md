@AGENTS.md

# Claude Code 전용

규칙의 정본은 AGENTS.md와 docs/agents다. 이 파일에는 Claude Code에만 있는 기능을 적는다.

## 학습 모드 패널 (study-mode)
- `.claude/skills/study-mode/`의 mod. 현재 작업, 직접 해보기, 지식 노트를 패널에 보여 주고 상태줄에 현재 작업을 표시한다.
- `/study`로 패널을 연다. 세션을 시작할 때도 자동으로 열린다(터미널 폭이 좁으면 대기한다).
- 학습 모드의 행동 규칙(질문 저장, progress 갱신)은 AGENTS.md와 docs/agents/learning-guide.md에 있다. 패널은 시스템 프롬프트를 바꾸지 않는다. 사용자 플러그인의 시스템 프롬프트 수정은 Claude Code 기본 보안 정책이 막는다.
- 패널은 `docs/progress/`와 `docs/learning/qna/` 파일을 읽어 보여 줄 뿐이다. 내용은 항상 파일에 쓴다.

## 플러그인 (project scope, 팀원은 각자 한 번 설치)
```
claude plugin install superpowers@claude-plugins-official --scope project
claude plugin marketplace add DietrichGebert/ponytail
claude plugin install ponytail@ponytail --scope project
brew install jdtls
claude plugin install jdtls-lsp@claude-plugins-official --scope project
```
사용 순서와 충돌 조정은 `docs/agents/quality-gates.md`를 따른다.
