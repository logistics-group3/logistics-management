# 스텝 23. 문서 지도와 작성 가이드

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 2·3장과 6장 결정(SA·ADR은 저장소 내부, 문서는 팩트 기반·간결), 한얼님 댓글 2개("progress가 TASK 알려 주는 느낌인가?", "서비스 협업 창구가 구체적으로 어떤 역할인가?")
- 결과: 문서 링크 점검은 PR 범위 작업을 모두 마친 뒤 한 번에 한다(스텝 33)

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `docs/README.md` | 문서 지도. 상황별로 읽을 문서, 정보별 기준 위치, 자주 묻는 것(progress, 서비스 협업 창구, 모듈 AGENTS.md와의 차이). 기존 `docs/agents/document-map.md`를 흡수하고 지웠다 |
| `docs/guides/writing.md` | 문서 작성 가이드. 통합안 원칙 8(평서체, AI 말투 금지), quality-gates(검증 근거, "미확인"), haneol(실제 코드 기준), KMH(정보마다 기준 위치 하나, "원인: 미확인") |
| `docs/services/README.md` | 서비스 협업 창구 목차, 서비스 간 계약 요약(document-map에서 이동), 협업 로그 규칙(OPEN/DECIDED/DONE)과 예시 |
| `docs/agents/git-workflow.md` → `docs/guides/git-workflow.md` | 사람과 에이전트가 함께 쓰는 팀 가이드라 guides로 옮겼다. 내용은 스텝 30에서 고친다 |
| `docs/SA/Sparta_logistics_SA_Master .md` → `docs/SA/sparta-logistics-sa.md` | 파일 이름의 공백 제거. 스크립트·링크에서 따옴표 없이 쓸 수 있다 |
| 루트 `README.md` 문서 절 | 문서 지도부터 읽게 정리 |

## 리뷰 포인트
1. **"작성 지침"의 출처**: Notion에서 말한 "팩트 기반으로 간결하게 쓰는 지침"과 정확히 같은 파일은 찾지 못했다. 사용자 결정에 따라 통합안 규칙(원칙 8, quality-gates)에 haneol·KMH 규칙을 더해 팀 가이드로 만들었다.
2. **progress는 할 일 목록이 아니다**: 브랜치를 이어받는 사람과 새 세션의 에이전트를 위한 인계 메모다. 팀 할 일 관리는 Notion에서 한다. 문서 지도에 예시를 넣었다.
3. **협업 로그를 받는 쪽 문서에 쓰는 이유**: 요청이 요청받는 서비스의 README에 모여 있어야 그 담당자가 한곳에서 확인할 수 있다.
