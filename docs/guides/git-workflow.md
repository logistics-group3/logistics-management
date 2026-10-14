# Git 작업 가이드

브랜치, 커밋, PR 규칙이다. 팀 합의(Notion "docs·AI 지침 통합 방안")를 정리했고, 원본은 haneol `doc/guide/13-git-workflow.md`다.

## 1. 브랜치
```
main          제출본. PR로만 합친다
├─ hotfix/*   main에서 딴 긴급 수정
└─ dev        통합 브랜치. PR로만 합친다
   └─ feat/*  dev에서 딴 작업 브랜치
```

| 브랜치 | 어디서 따나 | 어디로 합치나 | 이름 예 |
|---|---|---|---|
| `feat/{모듈}-{작업}` | `dev` | `dev` (squash) | `feat/hub-stock-inbound`, `feat/common-sort-helper` |
| `hotfix/{작업}` | `main` | `main` (merge), 이어서 `main`을 `dev`에 합친다 | `hotfix/gateway-401` |
| `dev` | 공통 인프라 PR 병합 직후의 `main` | `main` (merge) | |

- 버그 수정, 리팩터링, 문서 작업도 `dev`에서 따는 브랜치는 `feat/` 접두어를 쓴다. 종류는 커밋 type으로 나눈다.
- 브랜치 하나에 작업 하나만 담는다. 다른 서비스 모듈을 함께 고쳐야 하면 PR 본문에 이유를 쓴다. 다른 서비스 코드는 원칙적으로 그 서비스 협업 로그에 요청한다([서비스 협업 창구](../services/README.md)).
- `common`, `common-core`, `config-repo/application.yml`, `build.gradle`, `docker-compose.yml`, `infra/`는 모든 서비스에 영향을 준다. 이 파일을 고친 PR은 팀원 전체에게 리뷰를 요청한다.
- 작업 인계 파일은 `docs/progress/<브랜치명의 /를 -로 바꾼 이름>.md`다. 예: `feat/hub-stock-inbound` → `docs/progress/feat-hub-stock-inbound.md`

## 2. 커밋 메시지
```
type(scope): 요약

- 무엇을, 왜 (필요할 때만)
```

| type | 언제 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작이 바뀌지 않는 구조 변경 |
| `test` | 테스트만 |
| `docs` | 문서만 |
| `chore` | 빌드, 설정, 의존성 |

- scope는 모듈이나 영역 이름이다: `hub`, `order`, `common`, `gateway`, `config`, `infra`, `build`, `ci`, `agents`, `docs`. 예: `feat(hub): 허브 재고 차감 내부 API 추가 (I-05)`, `chore(config): order Feign 타임아웃 조정`
- 요약은 한국어 평서형으로 50자 안쪽에 쓰고 마침표를 찍지 않는다.
- 한 커밋에 한 가지 변경만 담는다. 빌드가 깨지는 중간 커밋을 만들지 않는다.
- `Co-Authored-By` 같은 AI 공동 작성자 표기를 넣지 않는다.

## 3. PR
- 제목은 커밋 형식(`type(scope): 요약`)으로 쓴다. `dev`에는 squash로 합치므로 PR 제목이 `dev`의 커밋 제목이 된다.
- 본문은 STAR 형식이다. `.github/pull_request_template.md`가 자동으로 채워진다.

| 항목 | 쓰는 것 |
|---|---|
| 상황 (Situation) | 왜 필요한가. SA 절, 협업 로그 ID(`HUB-001`), Notion 링크 |
| 과제 (Task) | 이 PR이 끝내야 하는 것과 범위 밖인 것 |
| 행동 (Action) | 무엇을 바꿨나. 계층·파일 단위로 |
| 결과 (Result) | 실행한 명령과 결과. 확인하지 못한 것은 "미확인"으로 적는다 |

- 리뷰어 1명 이상이 승인하면 합친다. 1장의 공통 영역을 고쳤으면 팀원 전체가 본다.
- `docs-guard` 검사(문서 동기화, 에러 코드)가 통과해야 한다. 문서에 영향이 없는 변경이면 `skip-docs` 라벨을 붙이고 본문에 이유를 쓴다.
- 스킬(`.agents/skills/`)을 추가·수정·삭제했으면 본문에 이유를 쓴다. 리뷰에서 팀 공용으로 둘지 판단한다.
- `dev` → `main`은 기능 묶음이 끝날 때(발표·제출 전) merge commit으로 합친다. PR 본문에 포함된 기능 PR 목록을 적는다.

## 4. 올리면 안 되는 것
`.env`, `.env.*`(`.env.example` 제외), 키 파일(`*.pem`, `*.key`, `*.p12`, `*.jks`), IDE 설정(`.idea/`), 빌드 결과(`build/`, `.gradle/`), 개인 에이전트 설정(`CLAUDE.local.md`, `.claude/settings.local.json`, `.claude/skills/`, `.claude/rules/`). 모두 `.gitignore`에 있다. 나에게만 필요한 제외 경로는 `.git/info/exclude`에 쓴다.

비밀값을 실수로 커밋했다면 되돌리는 것으로 끝나지 않는다. 값을 바로 바꾸고 팀에 알린다([설정 보안 가이드](config-security.md)).

## 5. 저장소 설정(관리자)
- `main`, `dev` 보호: PR 필수, 승인 1명, `docs-guard` 통과 필수, force push 금지
- `skip-docs` 라벨 생성

## 6. 에이전트 규칙
- 사용자가 요청할 때만 커밋하고 푸시한다.
- `main`, `dev`에서 직접 작업하지 않는다. 브랜치를 먼저 만든다.
- force push, `reset --hard`, 브랜치 삭제는 사용자 확인 후에만 한다.
- 커밋과 PR에 AI 공동 작성자 표기나 생성 도구 문구를 넣지 않는다.
- 커밋할 때는 경로를 지정해 `git add`한다. `.env`나 개인 설정이 섞이지 않게 한다.
