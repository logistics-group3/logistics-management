# integration/base-setting

## 지금 하는 일
Notion 팀 의견을 공통 인프라 통합 브랜치에 반영한다 (코드 + 문서·AI 지침)

## 진행 상태
- [x] 스텝 13 실행 환경 보강: 줄바꿈·인코딩·데몬 JDK·비밀값 무시 패턴 (compile 성공, 데몬 JVM 17 확인)
- [x] 스텝 14 Eureka 로컬 튜닝: 서버 자기 보호 해제, 갱신 5초·만료 15초·목록 조회 5초 (런타임 측정은 스텝 21)
- [x] 스텝 15 소유자 검증 헬퍼: `UserContextTest` 5개, `UserContextWebTest` 7개 통과
- [x] 스텝 16 common 조건부 자동 설정: 자동 설정 6개, 슬라이스 등록, common 34·user 17 테스트 통과 (ADR 0009)
- [x] 스텝 17 정렬 허용 목록·QueryDSL 도우미: QuerydslSupport, SortAllowList, InvalidSortException → 400 VALIDATION_ERROR(sort). common 48개 통과
- [x] 스텝 18 내부 API DTO 반환: I-01·Gateway 클라이언트 변경, 200 본문 테스트 추가, gateway 24·user 17 통과 (ADR 0010)
- [x] 스텝 19 MASTER 계정 자동 생성: 기동 시 .env 값으로 생성, 단위 5·통합 1 추가, user 23개 통과 (E2E 확인은 스텝 21)
- [x] 스텝 20 에러 코드 규칙·통합 문서: docs/error-codes.md, error-codes-check.sh(PASS, 실패 경로 확인), INVALID_STATUS_TRANSITION 공통화
- [x] 스텝 21 새로 시작 안내·전체 검증: build 98개 통과, E2E 18/0(새 볼륨), db-check 5/5, error-codes PASS, Eureka 강제 종료 17초
- [x] 스텝 22 학습 노트 사람별 폴더 이동: 링크 깨짐 0건
- [x] 스텝 23 문서 지도·작성 가이드: `docs/README.md`, `docs/guides/writing.md`, 협업 창구 규칙
- [x] 스텝 24 서비스 협업 창구 7개
- [x] 스텝 25 모듈 지침: 모듈 10개 `AGENTS.md`·`CLAUDE.md`
- [x] 스텝 26 팀원 ADR 이관: 공통 0011~0013, 서비스 ADR 9개(제안)
- [x] 스텝 27 레이어별 규칙 9개·리뷰 체크리스트
- [x] 스텝 28 공용 스킬 6개, `setup-agents.sh` (Claude Code·Codex 양쪽 인식 확인)
- [x] 스텝 29 팀 가이드 3개와 문제 해결 기록
- [x] 스텝 30 Git 작업 가이드와 STAR PR 템플릿
- [x] 스텝 31 `script/run.sh`: `--reset --check` E2E 15/0, 지정 서비스만 빌드·교체
- [x] 스텝 32 문서 동기화 검사·PR 검사 워크플로: 9가지 경우 확인
- [x] 스텝 33 최종 확인: build 98개 통과, 상대 링크 깨짐 0건, docs-check·error-codes PASS
- [ ] main 대상 PR 생성, 팀 리뷰 후 병합, main에서 dev 생성
- 면접 포인트: Gradle toolchain과 데몬 JVM 기준의 차이, 컴포넌트 스캔과 조건부 자동 설정의 차이, Eureka 응답 캐시와 인스턴스 만료 시간

## 다음 단계
1. `integration/base-setting`을 push하고 `docs/reviews/base-setting-integration-pr.md` 본문으로 main 대상 PR을 만든다(코드와 문서·AI 지침을 한 PR로, 2026-10-14 결정).
2. 팀 리뷰 후 병합되면 main에서 `dev`를 만들어 push한다.
3. 관리자: `skip-docs` 라벨 생성, main·dev 보호 규칙.

## 직접 해보기
1. `./gradlew --version`을 실행해 `Daemon JVM` 줄에 `Compatible with Java 17`이 보이는지 확인한다.
2. `git ls-files --eol gradlew script/e2e-check.sh`로 `eol=lf` 속성이 붙었는지 확인한다.

## 메모
- 결정 근거: Notion 문서 세 개(main 통합 방안, 팀 합의 대조·추가 검토, docs·AI 지침 통합 방안)
- 문서·AI 지침 정비는 처음에 PR B(dev 대상)로 나누려 했으나, 같은 브랜치에 담아 main 대상 PR 하나로 올리기로 했다(2026-10-14).
