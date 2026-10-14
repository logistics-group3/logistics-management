# PR 제목

feat(infra): 공통 인프라 통합과 문서·AI 지침 정비 — 팀 검토 의견 반영

## 상황 (Situation)
다섯 명(haneol, junhyun, KEJ, KMH, seungkeun)이 같은 공통 인프라 범위를 각자 구현했다. junhyun 브랜치를 기반으로 다른 브랜치의 보완 사항을 선별 통합한 후보(`81780df`)를 만들었고, 팀이 Notion 문서 세 개로 검토 의견을 남겼다.
- [main 통합 방안](https://app.notion.com/p/3f97bd823b758089bac4fc833688aa89)
- [main 통합 방안 — 팀 합의 대조·추가 검토](https://app.notion.com/p/3f97bd823b7581feb841e0c0da37eced)
- [docs·AI 지침 통합 방안](https://app.notion.com/p/3f97bd823b7581c0a9d1cf1da4b14362)

## 과제 (Task)
- 팀 의견 중 코드에 해당하는 결정을 반영하고, 「공통 인프라 개발 범위」의 완료 기준 6개를 유지한 채 main의 첫 기준선으로 병합한다.
- 문서·AI 지침 정비(Notion에서는 dev 대상 별도 PR로 나눴던 범위)도 같은 브랜치에 담는다. 담당자들이 dev에서 바로 같은 규칙으로 시작하게 하기 위해서다(준현 결정, 2026-10-14). 커밋을 스텝별로 나눠 두어 문서 범위만 따로 리뷰할 수 있다.
- 범위 밖: 도메인 기능, E2E 실패 경로(도메인 담당자가 필요할 때 추가), 설정 자동 refresh(팀 결정으로 제외).

## 행동 (Action)
### 코드
| 스텝 | 커밋 | 내용 | 출처 |
|---|---|---|---|
| 기반 | `81780df` | junhyun 공통 인프라 + 헤더 검증 공유, 반복 논리 삭제 방어, DB 계정 환경 변수 초기화, public 스키마 차단 | junhyun, haneol, KEJ, KMH, seungkeun |
| 13 | `chore(build)` | `.gitattributes` LF 고정, UTF-8 컴파일, 데몬 JDK 17 고정, 비밀값 무시 패턴 | KEJ, KMH, seungkeun, haneol |
| 14 | `chore(eureka)` ×2 | 자기 보호 해제, 갱신 5초·만료 15초·조회 5초, 읽기 전용 응답 캐시 해제 | 네 명 공통, KMH 값 |
| 15 | `feat(common)` | `UserContext` 소유자 검증 헬퍼(`validateSelfOrMaster` 등) | haneol |
| 16 | `refactor(common)` | common 공통 빈을 조건부 자동 설정 6개로 등록, 묶음별 끄기·재정의, 슬라이스 테스트 포함 (ADR 0009) | KEJ, KMH, seungkeun |
| 17 | `feat(common)` | 정렬 허용 목록(`SortAllowList`, `QuerydslSupport`), `notDeleted`, `PageResponse.from(page, mapper)` | seungkeun |
| 18 | `refactor(user)` | 내부 API(I-01)는 DTO 그대로 반환, Gateway 클라이언트 변경 (ADR 0010) | haneol, KMH |
| 19 | `feat(user)` | 기동 시 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`로 MASTER 계정 자동 생성 | KMH, KEJ |
| 20 | `docs(common)` | 에러 코드 이름 규칙, `docs/error-codes.md`, `script/error-codes-check.sh`, `INVALID_STATUS_TRANSITION` 공통화 | KMH, haneol |
| 21 | `docs(infra)` | 통합 후 DB 새로 시작 안내, `init.sh` 주석, 검증 기록 | 팀 결정 |

### 문서·AI 지침
| 스텝 | 커밋 | 내용 | 출처 |
|---|---|---|---|
| 22 | `docs(learning)` | 학습 노트를 사람별 폴더(`docs/learning/<이름>/`)로 이동 | 팀 결정 |
| 23 | `docs` | 문서 지도(`docs/README.md`), 문서 작성 가이드, 협업 창구 규칙, SA 파일 이름 정리 | KMH, haneol, 팀 결정 |
| 24 | `docs(services)` | 서비스 7개 협업 창구(제공 API, 호출 API, 구현 현황, 협업 로그) | KMH `agent.md` |
| 25 | `docs(agents)` | 모듈 10개 `AGENTS.md`(작업자 지침)와 `CLAUDE.md`(`@AGENTS.md`) | KMH, 팀 결정 |
| 26 | `docs(decisions)` | 공통 ADR 0011~0013, 서비스 ADR 9개(상태 `제안`), 기존 ADR 보완 | seungkeun |
| 27 | `docs(agents)` | 레이어별 코드 규칙 9개(`paths:` 머리말), 리뷰 체크리스트 | haneol |
| 28 | `feat(agents)` | 공용 스킬 6개 재구성, `script/setup-agents.sh`(Claude Code 연결) | KMH, junhyun |
| 29 | `docs(guides)` | 구현·로컬 실행·설정 보안 가이드, 문제 해결 기록 | haneol, KEJ, KMH |
| 30 | `docs(guides)` | Git 흐름(main ← dev ← feat/*, hotfix/*), STAR PR 템플릿 | 팀 결정 |
| 31 | `feat(infra)` | `script/run.sh`(`.env` 준비, `--reset`, `--check`, 서비스·`--scale` 전달) | haneol, KEJ |
| 32 | `chore(ci)` | 문서 동기화 검사(`docs/doc-map.tsv`, `script/docs-check.sh`), PR 검사 워크플로 | haneol |

스텝별 설명은 `docs/learning/junhyun/steps/step-13` ~ `step-33`에 있다.

## 결과 (Result)
### 코드와 실행
| 검사 | 결과 |
|---|---|
| `./gradlew clean build` (마지막 커밋 기준) | 성공. 테스트 98개(기반 70개), 실패·오류·skip 0 |
| `script/e2e-check.sh --up --with-cache-ttl` (새 볼륨, 별도 Compose 프로젝트) | PASS 18 / FAIL 0. 기동 시 만든 MASTER 로그인 포함 |
| `script/run.sh --reset --check` (마지막 스텝, 새 볼륨, 별도 Compose 프로젝트) | E2E PASS 15 / FAIL 0 |
| `script/db-check.sh` | 서비스 계정 5개 인증·스키마 격리 PASS |
| `script/error-codes-check.sh` | 에러 코드 15개, 중복·문서 누락 없음 |
| Eureka 등록 해제 측정 | 정상 종료 0초, 강제 종료 17초(응답 캐시 해제 전 60초) |

### 문서·AI 지침
| 검사 | 결과 |
|---|---|
| 저장소 전체 Markdown 상대 링크 | 깨진 링크 0개 |
| `script/docs-check.sh origin/main` | OK. 누락·갱신·훅·테스트 전용·없는 ref 등 9가지 경우 확인(스텝 32) |
| Claude Code(`claude -p`) | 스킬 6개 인식. presentation 파일을 열면 presentation 규칙과 user-service 지침을, domain 파일을 열면 domain 규칙만 불러옴 |
| Codex(`codex exec`, 0.160.0) | `.agents/skills`의 스킬 6개 인식, 루트 `AGENTS.md` 시작 절차 인용 |
| `script/run.sh` 서비스 지정·`--scale` | 지정한 서비스만 빌드·교체(8초), hub 2개 Eureka UP 2 |
| `git ls-files --eol` | 스크립트 8개와 `gradlew` 모두 LF |
| GitHub Actions `docs-guard` (PR #1) | `docs-check` pass, `error-codes-check` pass |

확인하지 못한 것: Windows에서 clone·실행과 `setup-agents.sh`의 복사 대체 경로, 팀원별 IDE import, Claude Code 종료 훅 연결(개인 선택).

## 리뷰 포인트
1. common 자동 설정(ADR 0009): 서비스 메인 클래스의 `scanBasePackages`를 없앴다. 공통 동작을 바꾸려면 같은 타입의 빈을 정의하고, 묶음 전체를 끌 때만 `common.<묶음>.enabled=false`를 쓴다.
2. 내부 API 응답(ADR 0010): FeignClient 반환 타입을 DTO로 쓴다. 실패는 SA 6.1 에러 형식 그대로다.
3. MASTER 자동 생성: 같은 username이 이미 있으면 승격하지 않고 건너뛴다.
4. 에러 코드: 서비스 코드는 `{리소스}_{상황}`, 여러 서비스가 쓰는 코드는 `CommonErrorCode`에만 둔다.
5. 지침 정본 위치: 규칙은 `AGENTS.md`·`docs/agents/`, 스킬은 `.agents/skills/`에 두고 Claude Code는 `script/setup-agents.sh`로 연결한다. 링크는 Git에 올리지 않는다(Windows 심볼릭 링크 문제).
6. 서비스 ADR 9개는 상태 `제안`이다. SA와 다른 점을 적어 두었으니 서비스 담당자가 확정하거나 거절한다.
7. 문서 동기화 검사는 다른 담당자가 읽는 문서(API 계약, 에러 코드, 실행·비밀값 안내)만 매핑했다. 매핑을 넓힐지 리뷰에서 정한다.

## 팀원에게 알릴 것
- 기존 `.env`에 `.env.example`의 `MASTER_USERNAME`, `MASTER_PASSWORD` 두 줄을 추가한다.
- 통합 후 처음 실행은 `script/run.sh --reset`(또는 `docker compose down -v`)으로 DB를 새로 시작한다.
- Claude Code를 쓰면 clone 후 `script/setup-agents.sh`를 한 번 실행한다. Codex는 따로 할 일이 없다.
- 이 PR이 병합되면 main에서 `dev`를 만든다. 이후 작업은 `dev`에서 `feat/{모듈}-{작업}` 브랜치로 한다([Git 작업 가이드](../guides/git-workflow.md)).
- 저장소 관리자는 `skip-docs` 라벨을 만들고 main·dev 보호 규칙을 건다.
- 학습 노트는 `docs/learning/<이름>/`에 각자 쓴다.
- Windows에서 이미 clone했다면 줄바꿈 규칙을 적용하기 위해 한 번 다시 checkout한다(`step-13` 참고).

## 관련 문서
- 결정: `docs/decisions/0008`~`0013`, 서비스 결정 `docs/services/{service}/decisions/`
- 문서 지도: `docs/README.md`, 팀 가이드: `docs/guides/`
- 범위 대조와 선별 근거: `docs/reviews/base-setting-integration.md`
- 에러 코드: `docs/error-codes.md`
