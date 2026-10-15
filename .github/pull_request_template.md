<!-- 제목은 type(scope): 요약. dev에는 squash로 합치므로 제목이 커밋 제목이 된다. 규칙: docs/guides/git-workflow.md -->

## 상황 (Situation)
<!-- 왜 필요한가. SA 절, 협업 로그 ID(HUB-001), Notion 링크 -->

## 과제 (Task)
<!-- 이 PR이 끝내야 하는 것과 범위 밖인 것 -->

## 행동 (Action)
<!-- 무엇을 바꿨나. 계층·파일 단위로 -->

## 결과 (Result)
<!-- 실행한 명령과 결과. 확인하지 못한 것은 "미확인"으로 적는다 -->
| 확인 | 결과 |
|---|---|
| `./gradlew build` | |
| `script/docs-check.sh` | |

## 리뷰 포인트
<!-- 리뷰어가 먼저 볼 곳, 판단이 필요한 곳 -->

## 체크
- [ ] 문서 갱신: 협업 창구, `docs/error-codes.md`, ADR 등 (`script/docs-check.sh`). 문서 영향이 없으면 `skip-docs` 라벨과 이유
- [ ] 스킬 변경(`.agents/skills/`): 없음 / 있음(이유: )
- [ ] 설정 변경 시 다시 시작할 서비스: 없음 / 있음( )
- [ ] 비밀값(`.env`, 토큰, 실제 비밀번호)이 없다
- [ ] [리뷰 체크리스트](../docs/guides/review-checklist.md) 셀프 점검
