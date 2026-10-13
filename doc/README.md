# doc/ — 스파르타 물류 설계 문서

> **기준은 Notion SA 문서(스파르타물류_SA문서_Master)** 입니다. 이 폴더는 그중 **코드로 구현된 내용**을 코드와 함께 관리합니다.
> Notion과 다르게 구현한 부분은 `etc/06-adr.md`에 이유를 남깁니다.

역할별로 파일 하나씩, 번호 순서 = 읽는 순서입니다.

```
doc/
├── README.md              목차 + 문서 관리 규칙
├── design/
│   ├── 01-overview.md     범위·일정·담당
│   ├── 02-architecture.md 서비스 구성, 프로젝트 구조, common 모듈, 인증/인가, 통신, 인프라·실행
│   ├── 03-erd.md          공통 규칙 + DB별 테이블
│   └── 04-api.md          공통 규약 + 서비스별 API
├── guide/                 역할별 가이드 (설계가 아니라 "어떻게 일하나")
│   ├── 11-dev-guide.md        서비스 담당자: 도메인 하나를 구현하는 순서
│   ├── 12-config-ops.md       인프라 담당: config 보안 관리 · 무중단 설정 변경
│   ├── 13-git-workflow.md     전원: 브랜치 · 커밋 · PR (제안)
│   ├── 14-review-checklist.md 리뷰어: PR 체크리스트
│   ├── 15-troubleshooting.md  전원: 로컬 실행 문제 해결
│   └── 16-local-run.md        전원: 서버 올리기 · 바꾼 것만 다시 올리기
├── etc/
│   ├── 06-adr.md          결정 기록 (Notion과 다른 점 포함)
│   ├── 07-changelog.md    문서 변경 이력
│   └── doc-map.tsv        코드 경로 → 갱신할 문서 매핑 (점검 스크립트가 읽음)
└── sql/                   서비스별 DDL (DB 구조의 원본, 각 DB 컨테이너가 하나씩 실행)
    ├── 01-user.sql
    ├── 02-hub.sql         (담당자 작성)
    ├── 03-company.sql
    ├── 04-order.sql
    └── 05-delivery.sql
```

설정은 루트 [`config/`](../config/README.md), 코드 컨벤션은 [`.claude/rules/`](../.claude/rules/README.md)에 있습니다.
### 역할별로 먼저 볼 문서

| 역할 | 문서 |
|---|---|
| 서비스 담당자 (hub·company·order·delivery·user 관리) | `guide/11-dev-guide.md` → `.claude/rules/` → `design/03·04` |
| 인프라 담당 (config·compose·gateway) | `design/02-architecture.md` → `guide/12-config-ops.md` |
| 리뷰어 | `guide/14-review-checklist.md` |
| 처음 실행하는 사람 | 루트 `README.md` → `guide/16-local-run.md` → `guide/15-troubleshooting.md` |

흐름 문서(05-flow: 주문·배송 호출 순서)는 도메인 구현 시작 후 추가합니다.

## 문서 관리 규칙

| 바꾼 코드 | 갱신할 곳 |
|---|---|
| `{service}`의 Entity·컬럼 | `sql/{NN}-{service}.sql` + `03-erd.md` |
| `{service}`의 Controller·presentation | `04-api.md` → `{service}-service` 섹션 |
| FeignClient, Slack, 보상 로직 | `02-architecture.md` → 5. 서비스 간 통신 |
| gateway-service | `02-architecture.md` → 4. 인증 / 인가 |
| common 모듈 | `02-architecture.md` → 3. common 모듈 |
| docker-compose, Dockerfile, config-server | `02-architecture.md` → 6. 인프라·실행 |
| `config/*.yml`, settings.gradle, build.gradle | `02-architecture.md` → 1·2 |
| Notion SA와 다른 결정 | `06-adr.md` 맨 위에 ADR 추가 |

1. 코드와 문서는 **같은 커밋/PR**에서 수정
2. `07-changelog.md` 맨 위 오늘 날짜 섹션에 한 줄: `- [영역/서비스] 내용 (관련 파일) — 작성자`
3. 문서 영향이 없는 변경 → CHANGELOG에 `- [규칙] 문서 영향 없음: <이유>` 또는 PR에 `skip-docs` 라벨
4. 새 서비스 DDL 추가 시 `doc-map.tsv` 패턴도 확인

### 자동 점검 (비용 0원)

| 단계 | 장치 | 동작 |
|---|---|---|
| Claude Code 작업 중 | 루트 `CLAUDE.md` | 코드 수정 시 문서·CHANGELOG도 함께 수정하도록 지시 |
| Claude Code 작업 종료 시 | `.claude/settings.json` Stop 훅 | `script/docs-check.sh --hook` → 누락 시 Claude가 이어서 갱신 |
| 커밋 전 (수동) | `./script/docs-check.sh` | 누락 확인 |
| PR | `.github/workflows/docs-guard.yml` | 누락 시 체크 실패 (`skip-docs` 라벨로 예외) |
