# 공통 인프라 통합 PR 전수검사

- 날짜: 2026-10-15 (KST)
- 대상: PR #1 `integration/base-setting` → `main` (`438ffbd` 기준)
- 방법: 다섯 영역(코드, 인프라·스크립트, 문서와 코드 대조, 결정 기록·팀 결정 반영, AI 지침 설정)을 나눠 검사했다. 영향이 큰 항목은 직접 다시 실행해 확인했다. 저장소 파일은 검사 중에 고치지 않았다.
- 처리: 병합 전에 고칠 7건은 이 PR에서 고쳤다(1장). 나머지는 `dev`에서 따로 처리한다(2장).

## 문제없이 확인된 것
- `./gradlew clean build` 테스트 98개 통과. 테스트 이름과 실제 검증 내용이 맞다.
- 팀 결정 21개가 모두 저장소에 반영돼 있다. 실패 경로 E2E 보류만 PR 본문에 있다.
- common 자동 설정의 조건·순서·슬라이스 등록이 전체 앱, WebMvc 슬라이스, `@DataJpaTest` 슬라이스에서 동작한다. 빈 이중 등록이 없다.
- 정렬 허용 목록, 내부 API 계약(I-01), Gateway 보안, MASTER 동시 생성 처리.
- 포트, `init.sh`와 config-repo의 계정·스키마, V1 SQL과 Entity가 서로 맞다.
- 레이어 규칙 `paths:`가 실제 파일과 맞고, Claude Code와 Codex 모두 스킬 6개를 인식한다.
- 커밋 작성자, AI 공동 작성자 표기 없음, 개인 절대 경로·비밀값 패턴 없음, 깨진 상대 링크 0건.

## 1. 이 PR에서 고친 것
| # | 문제 | 확인 방법 | 수정 |
|---|---|---|---|
| 1 | `script/setup-agents.sh`가 `.claude/skills`, `.claude/rules` 안을 모두 지웠다. 개인 스킬·규칙이 복구 불가로 사라지고, 폴더가 링크면 `.agents/skills`까지 지워졌다 | 사본에서 재현 | 링크는 가리키는 원본으로, 복사본은 목록 파일(`.claude/.setup-agents-files`)로 자기 결과물만 알아보고 지운다. 이름이 겹치면 건너뛰고 알린다. 폴더가 링크면 중단한다. 8가지 경우를 `/bin/bash` 3.2로 확인했다 |
| 2 | "Config Server 값이 환경 변수보다 우선한다"는 설명이 반대였다. DB 포트가 5432로 고정돼, 포트를 바꾼 병행 환경에서 IDE로 띄운 서비스가 다른 스택의 DB에 붙을 수 있었다 | Config Server가 `server.port: 8080`을 줄 때 `SERVER_PORT=18080`인 Gateway가 18080으로 떴다 | DB URL에 `${DB_PORT:5432}`를 넣었다(`DB_PORT=1`로 띄워 `localhost:1` 접속 시도 확인). 우선순위 설명을 `config-repo/application.yml`, 설정 보안·로컬 실행 가이드, 설정 규칙, README, 문제 해결 기록에서 고쳤다 |
| 3 | Feign 문서가 타임아웃·연결 실패도 `BusinessException`이라고 했다. 실제로는 `FeignException`이라, 문서대로 짜면 주문 흐름이 타임아웃 보상을 놓친다 | `FeignErrorDecoder`, `GlobalExceptionHandler` 코드 확인 | `rules/feign.md`에 실패 종류별 예외 표와 두 예외를 모두 잡는 보상 예시를 넣었다. `rules/service.md`, ADR 0013도 고쳤다. 재시도는 응답 없는 실패에만 일어난다고 적었다 |
| 4 | `script/db-check.sh`가 127.0.0.1로 접속해 비밀번호를 검사하지 않았다(이미지 기본 pg_hba가 trust) | 임시 postgres:16 컨테이너에서 틀린 비밀번호로 접속 성공을 재현 | 컨테이너 IP로 접속해 scram-sha-256 인증을 거친다. 틀린 비밀번호로 실패(exit 2)하는 것을 확인했다 |
| 5 | `script/error-codes-check.sh`가 공백 4칸 들여쓰기만 인식했고, 추적하지 않는 사본(`.claude/worktrees`)까지 읽었다 | 사본에서 재현 | 공백·탭 들여쓰기를 모두 인식하고, `git ls-files`로 루트 모듈의 main 소스만 읽는다. 탭 들여쓰기 중복·문서 누락이 실패로 잡히는 것을 확인했다 |
| 6 | ADR 0008과 `docs/reviews/base-setting-integration.md`에 팀이 뒤집은 판단(자동 설정·정렬 도우미 보류, MASTER 자동 생성 제외, DB 이행 검토)이 그대로 있었다 | 문서 대조 | 0008에 "후속 결정" 절을 두고 바뀐 항목 5개를 적었다. 검토 문서에는 "→ 이후 변경"을 붙였다 |
| 7 | `AGENTS.md`가 하루 정리를 "알아서 기록"과 "제안" 양쪽에 넣었다 | 문서 대조 | 하루 정리는 제안한 뒤 사용자가 원할 때만 쓴다고 통일했다(`AGENTS.md`, `learning-note` 스킬) |

함께 고친 것: 셸 스크립트 두 곳의 `$변수` 바로 뒤 한글(bash 3.2가 변수 이름의 일부로 읽는다), `.gitignore`에 `.claude/worktrees/`, 설정 보안 가이드 S6이 PR 템플릿에 없는 칸을 가리키던 것.

## 2. 후속 작업 (`dev`에서)
### 코드
- [ ] Gateway: I-01이 형식이 틀린 200 응답(예: 구버전 user-service의 `ApiResponse` 포장)을 주면 빈 사용자 정보를 60초 캐시하고 NPE로 503을 낸다. `userId`·`role`이 없으면 오류로 처리해 캐시하지 않고, 비교는 `tokenRole.equals(user.role())`로 한다(`UserServiceClient`, `UserVerifyingJwtConverter`).
- [ ] MASTER 생성: 비밀번호 규칙 검사가 없어 `MASTER_PASSWORD=1`도 만들어지고, 72바이트를 넘으면 기동이 실패한다. `DataIntegrityViolationException`을 모두 "동시 생성"으로 보고 넘겨 다른 원인을 가린다. 같은 이름의 일반 사용자가 있으면 로그 없이 건너뛴다(`MasterAccountInitializer`).
- [ ] `common.openapi.enabled=false`면 `@CurrentUser`가 API 문서에 쿼리 파라미터로 나온다(`CommonOpenApiAutoConfiguration` static 블록).
- [ ] 하위 서비스의 `VALIDATION_ERROR` `errors[]`가 Feign을 거치며 빠진다. `QuerydslSupport.toOrderSpecifiers`가 `ignoreCase`를 무시한다.
- [ ] 테스트 보강: `common.jpa-auditing`·`common.querydsl` 끄기, 사용자 정의 빈 우선, `AutoConfigureDataJpa.imports` 등록. `jsonPath("$.hubId").doesNotExist()`는 필드가 없어져도 통과한다.

### 설정·스크립트
- [ ] compose의 `JWT_SECRET`, `POSTGRES_USER`, `POSTGRES_PASSWORD`에 `:?`가 없어 값이 비면 재시작을 반복한다.
- [ ] Gateway가 0.0.0.0에 열려 있고 `.env.example`의 MASTER 비밀번호가 공개돼 있다. 기본 바인딩을 127.0.0.1로 둘지 정한다.
- [ ] `docs-check.sh`가 `git diff` 실패(merge base 없음)에도 통과한다. `promote-master.sh`가 없는 사용자에게도 "변경"을 출력한다. `e2e-check.sh`가 모르는 옵션을 무시하고 `.env` 값의 따옴표를 벗기지 않는다.
- [ ] postgres healthcheck `pg_isready`에 `-h`가 없어 초기화 스크립트 실행 중에 healthy가 될 수 있다(미확인).
- [ ] `.dockerignore`의 `*.md`는 루트만 제외한다(`**/*.md`). `.gitignore`에 IDE 산출물(`bin/`, `.project`, `.classpath`, `.settings/`), `*.log`가 없다.
- [ ] `docs/doc-map.tsv`: config-repo 서비스 설정, `init.sh`, `Dockerfile`이 매핑되지 않았다. `common-core/*ErrorCode.java` 패턴이 `ErrorCode` 인터페이스까지 잡는다.

### 문서
- [ ] README: MASTER 승격 뒤 최대 60초 동안 `TOKEN_ROLE_MISMATCH`가 날 수 있다(Gateway 캐시).
- [ ] dev-guide·PR 템플릿의 `script/docs-check.sh`는 커밋 후에는 검사할 것이 없다. `script/docs-check.sh origin/dev`로 안내한다.
- [ ] user-service가 controller 규칙과 다르다(`Result` 반환, `@Tag`·`@Operation` 없음). 코드를 맞출지 규칙에 예외로 둘지 팀이 정한다.
- [ ] `docs/error-codes.md`: `INVALID_REQUEST`는 Gateway의 모든 400과 MVC의 매핑되지 않은 4xx에도 쓰인다.
- [ ] 서비스 재기동 직후 503 구간 설명에 LoadBalancer 캐시를 넣는다(측정 필요).
- [ ] ADR: order 0003의 SA 인용 오류(SA 7.1 ④는 커밋 후 비동기 발송을 명시), company 0001·order 0001의 SA와 다른 점 누락, 서비스 ADR의 Notion 번호(ADR-10 등)를 저장소 링크로, 0004 `QuerydslConfig`·0007 `init.sql`·0003 `GlobalFilter` 표기, 상태 값 형식, 0007·0012 팀 확인 기록, Config Server 도입 근거.
- [ ] `docs/services/README.md`의 order → hub 호출(SA 2.3에 없음), gateway README에 화이트리스트, `docs/doc-map.tsv`가 가리키는 gateway 절.
- [ ] 개인 도구·방식이 팀 규칙처럼 적힌 곳: `handoff` 스킬의 학습 패널·상태줄, `learning-guide.md`의 필수 `roadmap.md`와 `steps/`, `quality-gates.md`의 `--scope project` 설치와 study-mode.
- [ ] `coding-conventions.md`: 모듈 `application.yml`에 "포트"(실제로는 config-repo), `ApiResponse`·`ErrorCode` 위치(common-core).
- [ ] 스킬: `handoff` 예시 브랜치 이름(`feat/…`로), `troubleshooting`·`impl-service` 등의 KMH 원본 경로 안내, `adr` 스킬의 서비스 결정 README 생성 안내, `rules/test.md` `paths:`에 `**/src/testFixtures/**`.
- [ ] I-11·I-12 멱등 기준(SA 3.5와 7.3이 서로 다르다), TS-002의 "이 저장소" 차이, 줄바꿈 재적용 명령의 작업 내용 삭제 경고.
