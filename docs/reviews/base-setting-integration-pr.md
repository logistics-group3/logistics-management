# PR 제목

feat(infra): 공통 인프라 통합 — 팀 검토 의견 반영

## 상황 (Situation)
다섯 명(haneol, junhyun, KEJ, KMH, seungkeun)이 같은 공통 인프라 범위를 각자 구현했다. junhyun 브랜치를 기반으로 다른 브랜치의 보완 사항을 선별 통합한 후보(`81780df`)를 만들었고, 팀이 Notion 문서 세 개로 검토 의견을 남겼다.
- [main 통합 방안](https://app.notion.com/p/3f97bd823b758089bac4fc833688aa89)
- [main 통합 방안 — 팀 합의 대조·추가 검토](https://app.notion.com/p/3f97bd823b7581feb841e0c0da37eced)
- [docs·AI 지침 통합 방안](https://app.notion.com/p/3f97bd823b7581c0a9d1cf1da4b14362)

## 과제 (Task)
- 팀 의견 중 코드에 해당하는 결정을 반영하고, 「공통 인프라 개발 범위」의 완료 기준 6개를 유지한 채 main의 첫 기준선으로 병합한다.
- 문서·AI 지침 정비는 이 PR이 병합된 뒤 `dev` 대상 별도 PR로 진행한다(Notion 결정).

## 행동 (Action)
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

스텝별 설명은 `../learning/seungkeun/steps` ~ `step-21`에 있다.

## 결과 (Result)
| 검사 | 결과 |
|---|---|
| `./gradlew clean build` | 성공. 테스트 98개(기반 70개), 실패·오류·skip 0 |
| `script/e2e-check.sh --up --with-cache-ttl` (새 볼륨, 별도 Compose 프로젝트) | PASS 18 / FAIL 0. 기동 시 만든 MASTER 로그인 포함 |
| Eureka 설정 변경 뒤 `script/e2e-check.sh` 재실행 | PASS 15 / FAIL 0 |
| `script/db-check.sh` | 서비스 계정 5개 인증·스키마 격리 PASS |
| `script/error-codes-check.sh` | 에러 코드 15개, 중복·문서 누락 없음 |
| Eureka 등록 해제 측정 | 정상 종료 0초, 강제 종료 17초(응답 캐시 해제 전 60초) |

확인하지 못한 것: Windows에서 clone·실행, 팀원별 IDE import.

## 리뷰 포인트
1. common 자동 설정(ADR 0009): 서비스 메인 클래스의 `scanBasePackages`를 없앴다. 공통 동작을 바꾸려면 같은 타입의 빈을 정의하고, 묶음 전체를 끌 때만 `common.<묶음>.enabled=false`를 쓴다.
2. 내부 API 응답(ADR 0010): FeignClient 반환 타입을 DTO로 쓴다. 실패는 SA 6.1 에러 형식 그대로다.
3. MASTER 자동 생성: 같은 username이 이미 있으면 승격하지 않고 건너뛴다.
4. 에러 코드: 서비스 코드는 `{리소스}_{상황}`, 여러 서비스가 쓰는 코드는 `CommonErrorCode`에만 둔다.

## 팀원에게 알릴 것
- 기존 `.env`에 `.env.example`의 `MASTER_USERNAME`, `MASTER_PASSWORD` 두 줄을 추가한다.
- 통합 후 처음 실행은 `docker compose down -v`로 DB를 새로 시작한다.
- Windows에서 이미 clone했다면 줄바꿈 규칙을 적용하기 위해 한 번 다시 checkout한다(`step-13` 참고).

## 관련 문서
- 결정: `docs/decisions/0008`~`0010`
- 범위 대조와 선별 근거: `docs/reviews/base-setting-integration.md`
- 에러 코드: `docs/error-codes.md`
