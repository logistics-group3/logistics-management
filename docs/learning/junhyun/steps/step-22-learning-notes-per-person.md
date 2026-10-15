# 스텝 22. 학습 노트를 사람별 폴더로

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 6장 개인 학습 노트 "서로 학습 내용 공유하면 좋을 듯해서 제안대로". 이 스텝부터 문서·AI 지침 정비(PR B 범위)를 같은 브랜치에서 진행한다(사용자 결정).
- 결과: `docs/learning/` 아래 상대 링크 깨짐 0건(스크립트로 모든 md 링크 대상 존재 확인)

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `docs/learning/{README.md, roadmap.md, qna/, steps/}` → `docs/learning/junhyun/` | junhyun 개인 기록 32개와 이번 통합 스텝 기록을 개인 폴더로 이동 |
| `docs/learning/README.md` | 새 목차. 사람별 폴더 규칙 |
| `steps/*.md` | `../../decisions/` 상대 링크를 한 단계 깊게 수정(12개 파일) |
| `AGENTS.md`, `CLAUDE.md`, `README.md`, `build.gradle` Note 주석, `learning-note` 스킬, `learning-guide.md`, `coding-conventions.md`, `docs/progress/junhyun-base-setting.md`, PR 본문 | 경로 갱신. 규칙 문서는 `docs/learning/<이름>/`으로 일반화 |

## 리뷰 포인트
1. **사람별 폴더의 이유**: 다섯 명이 같은 `qna/README.md` 표에 한 줄씩 추가하면 거의 모든 PR에서 충돌이 난다. 폴더를 나누면 남의 파일을 고칠 일이 없다.
2. **`0002` ADR의 경로는 고치지 않았다**: 결정 당시의 기록이라 그대로 둔다. 폴더 단위(`docs/learning/`) 설명이라 여전히 맞다.
