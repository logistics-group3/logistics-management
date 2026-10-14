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
- [ ] 스텝 23~ 문서 지도·작성 가이드, 서비스 협업 창구, ADR 이관, 모듈 지침, 레이어 규칙, 스킬, 팀 가이드, run.sh, 문서 동기화 점검
- [ ] main 대상 PR 생성, 팀 리뷰 후 병합, main에서 dev 생성
- 면접 포인트: Gradle toolchain과 데몬 JVM 기준의 차이, 컴포넌트 스캔과 조건부 자동 설정의 차이, Eureka 응답 캐시와 인스턴스 만료 시간

## 다음 단계
1. `integration/base-setting`을 push하고 `docs/reviews/base-setting-integration-pr.md` 본문으로 main 대상 PR을 만든다.
2. 병합 후 main에서 `dev`를 만들고, 문서·AI 지침 정비(PR B)를 dev에서 분기해 진행한다.

## 직접 해보기
1. `./gradlew --version`을 실행해 `Daemon JVM` 줄에 `Compatible with Java 17`이 보이는지 확인한다.
2. `git ls-files --eol gradlew script/e2e-check.sh`로 `eol=lf` 속성이 붙었는지 확인한다.

## 메모
- 결정 근거: Notion 문서 세 개(main 통합 방안, 팀 합의 대조·추가 검토, docs·AI 지침 통합 방안)
- 문서·AI 지침 정비는 PR B로 분리한다(dev 대상).
