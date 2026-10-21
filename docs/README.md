# 문서 지도

무엇을 어디에 쓰고, 언제 무엇을 읽는지 정리한 문서다. 정보마다 기준 위치는 한 곳이고, 다른 곳에서는 링크만 건다. 작성 방법은 [문서 작성 가이드](guides/writing.md)를 따른다.

## 언제 무엇을 읽나

| 상황 | 읽을 문서 |
|---|---|
| 브라우저로 주문·배송을 테스트한다 | [운영 UI 실행 안내](../frontend/README.md), [검증 기록](reviews/logistics-test-ui.md) |
| 필수 기능을 시나리오대로 차례로 확인한다 | [시나리오 테스트 안내](guides/scenario-test.md), 운영 콘솔 `/guide` |
| Swagger로 API를 하나씩 테스트하며 서비스 간 요청 흐름을 본다 | [Swagger 수동 테스트와 요청 흐름 보기](guides/api-test.md) |
| 요청 수·오류·응답 시간 그래프를 본다 | [메트릭 모니터링](guides/monitoring.md) |
| 캐시 도입 전후 성능을 측정·비교한다 | [성능 측정 가이드](guides/performance-test.md), [측정 기록](reviews/performance/README.md) |
| 처음 합류했다, 로컬에서 띄워 보고 싶다 | 루트 `README.md` → [로컬 실행 가이드](guides/local-run.md) |
| 내 서비스 구현을 시작한다 | [서비스 협업 창구](services/README.md)의 내 서비스 → SA 해당 절 → [구현 가이드](guides/dev-guide.md) → 모듈 폴더의 `AGENTS.md` |
| 다른 서비스 API를 호출해야 한다 | 그 서비스의 `docs/services/{service}/README.md` 제공 API 표 |
| 에러 코드를 정하거나 찾는다 | [에러 코드 목록](error-codes.md) |
| 코드 리뷰를 한다 | [리뷰 체크리스트](guides/review-checklist.md), [레이어별 규칙](agents/rules/README.md) |
| 브랜치·커밋·PR을 만든다 | [Git 작업 가이드](guides/git-workflow.md) |
| 설정이나 비밀값을 다룬다 | [설정 보안 가이드](guides/config-security.md) |
| 막혔다 | [문제 해결 기록](troubleshooting.md) |
| 왜 이렇게 만들었는지 궁금하다 | [결정 기록](decisions/README.md) |
| AI 에이전트로 작업한다 | 루트 `AGENTS.md`(공통 진입점) |
| 프런트까지 전체 빌드하거나 API 연동을 검증한다 | [전체 빌드·검증](guides/full-build.md): Gradle과 npm을 각각 실행 |

## 정보별 기준 위치

| 정보 | 기준 위치 | 갱신 시점 |
|---|---|---|
| 설계(요구사항, API, ERD, 흐름) | `docs/SA/sparta-logistics-sa.md` | 팀 합의로 설계를 바꿀 때 |
| 서비스 실행 설정 | `config-repo/` | 설정을 바꿀 때 |
| 실행·검증 방법 | 루트 `README.md`, `.env.example`, `script/` | 실행 절차가 바뀔 때 |
| 공통 기술 결정 | `docs/decisions/` | 여러 서비스에 걸친 되돌리기 어려운 결정을 할 때 |
| 서비스 결정 | `docs/services/{service}/decisions/` | 한 서비스 안의 되돌리기 어려운 결정을 할 때 |
| 서비스 계약·구현 현황·협업 로그 | `docs/services/{service}/README.md` | API를 추가·변경하거나 다른 서비스에 요청할 때 |
| 구현 노트 | `docs/services/{service}/notes.md` | 기능 구현을 마쳤을 때(`impl-service` 스킬) |
| 에러 코드 | `docs/error-codes.md` | 에러 코드를 추가·변경할 때 |
| 팀 가이드(구현 순서, 리뷰, 실행, 설정, Git, 작성) | `docs/guides/` | 절차가 바뀔 때 |
| 문제 해결 기록 | `docs/troubleshooting.md` | 막혔던 문제를 해결했을 때(`troubleshooting` 스킬) |
| 에이전트 공통 규칙 | 루트 `AGENTS.md`, `docs/agents/` | 팀 규칙이 바뀔 때 |
| 레이어별 코드 규칙 | `docs/agents/rules/` | 코드 규칙이 바뀔 때 |
| 모듈 작업 지침 | `{module}/AGENTS.md` | 모듈 규칙이 바뀔 때 |
| delivery 작업 규칙·태스크 기록 | `delivery-service/docs/` | 태스크를 시작할 때, 커밋할 때 |
| 공용 스킬 | `.agents/skills/` | PR 리뷰에서 추가·수정·삭제를 판단한다 |
| 학습 노트 | `docs/learning/<이름>/` | 각자 |
| 브랜치 작업 인계 | `docs/progress/<브랜치명의 /를 -로 바꾼 이름>.md` | 작업을 시작·전환·종료할 때(`handoff` 스킬) |
| 통합 검토 기록 | `docs/reviews/` | 큰 통합·리뷰를 했을 때 |
| 회의록, 일정, 검토 토론 | Notion | 회의·검토 때 |

코드를 바꿀 때 함께 고쳐야 하는 문서는 `docs/doc-map.tsv`에 매핑돼 있고, PR마다 `script/docs-check.sh`가 확인한다.

## 자주 묻는 것

### `docs/progress/`는 할 일 목록인가?
아니다. 브랜치 하나의 **작업 인계 메모**다. "지금 무엇을 하고 있고, 어디까지 했고, 다음에 무엇을 하면 되는지"를 적는다. 그 브랜치를 이어받는 사람이나 새 세션의 에이전트가 이 파일만 읽고 바로 시작할 수 있게 하는 것이 목적이다. 팀 전체의 할 일 관리는 Notion에서 한다.

```markdown
# hub/route-search

## 지금 하는 일
허브 간 경로 조회 API(H-04) 구현

## 진행 상태
- [x] HubRoute 엔티티와 V2 마이그레이션 (통합 테스트 통과)
- [ ] QueryDSL 검색과 정렬 허용 목록

## 다음 단계
1. HubRouteQueryRepository에 출발·도착 허브 조건을 추가한다.
```

### 서비스 협업 창구(`docs/services/`)는 무엇을 하는 곳인가?
**다른 서비스 담당자가 내 서비스를 쓸 때 보는 곳**이다. 내가 제공하는 API의 요청·응답 필드와 에러 코드, 구현 현황, 다른 서비스와 주고받은 요청을 적는다.

예를 들어 delivery 담당자가 hub의 경로 조회 응답에 필드가 더 필요하면, hub 코드를 직접 고치지 않는다. `docs/services/hub/README.md` 협업 로그에 `OPEN`으로 요청을 남긴다. hub 담당자가 결정하면 `DECIDED`, 구현이 끝나면 `DONE`으로 바꾼다. 규칙은 [서비스 협업 창구](services/README.md)에 있다.

### 모듈의 `AGENTS.md`와 서비스 협업 창구는 어떻게 다른가?
| | `{module}/AGENTS.md` | `docs/services/{service}/README.md` |
|---|---|---|
| 읽는 사람 | 그 모듈 코드를 고치는 사람과 AI 에이전트 | 그 서비스를 호출하는 다른 서비스 담당자 |
| 내용 | 모듈 규칙(상태 전이, 컬럼 길이 등), 작업 전 읽을 것 | 제공 API 계약, 구현 현황, 협업 로그 |
| 바뀌는 빈도 | 규칙이 바뀔 때만 | 요청이 생길 때마다 |
