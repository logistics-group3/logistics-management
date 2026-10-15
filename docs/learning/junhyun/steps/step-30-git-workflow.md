# 스텝 30. Git 작업 가이드와 PR 템플릿

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" Git 항목(한얼 dev·main, 준현 main-dev-feat/hotfix, 승근 main-dev-feat, 민혁 STAR), 사용자 선택(커밋 제목 `type(scope)`, PR 본문 STAR, main 병합 후 dev 생성)
- 결과: `docs/guides/git-workflow.md`를 팀 합의대로 다시 쓰고 `.github/pull_request_template.md`를 추가했다.

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `docs/guides/git-workflow.md` | `main` ← `dev` ← `feat/{모듈}-{작업}`, 긴급은 `main`의 `hotfix/*`. 커밋 `type(scope): 요약`. dev는 squash, dev → main은 merge. 공통 영역 전체 리뷰, `skip-docs` 라벨, 스킬 변경 이유, 저장소 보호 설정, 에이전트 규칙 |
| `.github/pull_request_template.md` | STAR 네 칸, 결과 표(`./gradlew build`, `docs-check`), 문서 갱신·스킬 변경·재시작 서비스·비밀값·셀프 리뷰 체크 |
| `AGENTS.md`, `learning-note` 스킬, `docs/learning/README.md`, `learning-guide.md` | 학습 노트 폴더 이름을 "브랜치 접두어"가 아니라 팀원 영문 이름으로 정의 |

## 리뷰 포인트
1. **브랜치 이름에 사람 이름이 빠진다**: 이전 규칙(`{이름}/{작업}`)에서는 브랜치 접두어로 학습 노트 폴더를 정했다. `feat/*`로 바뀌어 이 연결이 끊기므로, 폴더 이름을 팀원 영문 이름 목록으로 직접 정의했다.
2. **squash와 merge를 나눈 이유**: dev에는 기능 PR 하나가 커밋 하나로 남아 이력이 기능 단위로 읽힌다. dev → main은 merge commit으로 "어느 기능 묶음이 언제 제출본에 들어갔는지"가 남는다. squash하면 PR 제목이 커밋 제목이 되므로 PR 제목도 `type(scope): 요약`으로 쓴다.
3. **`feat/` 하나로 통일**: 팀 합의가 feat/hotfix 두 종류라서 버그 수정·문서 작업도 `feat/`를 쓰고 종류는 커밋 type으로 나눴다. `fix/`, `docs/`를 따로 둘지는 팀이 정하면 이 표만 고치면 된다.
4. **저장소 보호 설정은 관리자 작업**: 문서에는 권장 설정만 적었다. 이 PR에서 GitHub 설정은 바꾸지 않았다.

## 팀원이 할 일
- 이 PR이 main에 병합되면 main에서 `dev`를 만든다. 이후 작업 브랜치는 `dev`에서 딴다.
