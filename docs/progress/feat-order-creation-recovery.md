# feat/order-creation-recovery

## 지금 하는 일
주문 생성 복구 커밋·푸시와 dev 대상 PR #27 생성 완료, 리뷰 대기다.

## 진행 상태
- [x] 기능 커밋 `7cebb13`을 feat/order-creation-recovery에 푸시하고 [PR #27](https://github.com/logistics-group3/logistics-management/pull/27)을 dev 대상으로 만들었다. origin/dev e55b2a3 위에 기능 변경만 구성했다. 기존 문서 PR #24는 유지했다.
- [x] 발행 직전 `./gradlew build` 2초 성공·58개 작업 UP-TO-DATE, PR 범위 docs-check·에러 코드 31개·공백·상대 링크 검사 통과. 소스와 ZIP 2개 내부 비밀값/토큰 일치 0건이다. 분리 전후 서비스 코드·실험 도구는 동일하다.
- [x] 사용자 요청으로 커밋·푸시·PR 발행을 시작했다. 기존 학습 문서 PR #24는 유지하고 기능 변경을 feat/order-creation-recovery에 분리한다. 이전 인계 내용은 이 파일에 보존했다.
- [x] 최종 F1/F2(-02)는 실제 SIGKILL(exit 137·OOMKilled=false)·재기동 뒤 복구 API로 ABORTED·I-17 CLOSED·활성 주문/배송/구간 0을 확인했다. 같은 키 409·같은 복구 무부작용 재사용·지연 생성 409도 통과했다. [최종 전후 결과](../reviews/order-creation-recovery/2026-10-20-implementation-results.md).
- [x] 최종 이력 API의 사유·예상 상태·요청자를 보강하고 backend build 47초 성공(5개 실행·53개 UP-TO-DATE), order 484개 재실행을 확인했다. 전체 XML 집계 857개 실패/오류/skip 0에는 재사용 결과가 포함된다. delivery 93개는 앞선 빌드에서 실행했다.
- [x] 최종 이미지에서 실제 Gateway 401/403/200, 이력의 원래 요청 정보·MASTER 감사자, V7·기존 4건 상태 보존을 재확인했다. 전용 스택 종료·볼륨 보존, 기존 12개 컨테이너 ID·현재 실행 상태를 확인했다. 일부 재시작 관찰을 TS-031에 기록했으며 기존 환경 무영향을 주장하지 않는다.
- [x] 구현 뒤 백엔드 전체 build 통과(최종 1분 29초, 58개 작업 중 9개 실행·49개 UP-TO-DATE). 전체 JUnit XML 857개 사례 실패·오류·skip 0, order 484·delivery 93 재실행. 프런트 build·lint·format·test 56개 통과.
- [x] 1차 F1 복구는 ABORTED·CLOSED·활성 주문/배송/구간 0·동일 키 409·같은 복구 무부작용 재사용을 확인했다. 1차 F2는 의도한 kill 전에 OOMKilled=true로 종료돼 제외했다. 전용 스택을 내리고 로컬 JVM/컨테이너 메모리 상한을 추가했다. 기존 UI 컨테이너 일부 재시작도 관찰했으나 직접 조작하지 않았고 원인은 확정하지 않았다.
- [x] 2026-10-20 사용자가 설계대로 복구 기능 구현·명령 기록·동일 F1/F2 전후 검증을 승인했다. ADR 0019와 DELIVERY-009에 계약을 기록했다. 이전 보고 시점의 중단 조건은 이번 요청으로 해제됐다.
- [x] 조건부 배송 종료·복구 실행권·감사 이력·MASTER API 구현과 회귀·실제 장애 복구 검증 완료. API·마이그레이션·한계는 ADR 0019와 운영 안내에 기록했다.
- [x] 2026-10-20 사용자 승인 범위: 격리 환경 준비·현재 코드 F1/F2 재현·실제 명령과 결과 기록. 복구 기능 구현 전에 재현 결과를 먼저 보고하고 멈춘다.
- [x] 전용 `logistics-recovery-test` 프로젝트·새 DB 볼륨·loopback 포트를 만들었다. 실제 order 컨테이너의 Slack 비활성·restart=no를 검증했다. 서비스 코드를 바꾸지 않고 JDI로 생성 처리 스레드만 멈춘다. 도구는 `script/recovery-experiment/`다.
- [x] 대조군 C0-F1-02·C0-F2-01은 중단점 해제 뒤 COMPLETED·활성 주문 1·배송 1·동일 키 201로 통과했다. 첫 C0-F1-01은 중단점 도달 전 접수 503으로 판정 제외하고 원문을 보존했다.
- [x] F1-01은 실제 order SIGKILL(exit 137)·재기동 뒤 PROCESSING·주문 0·배송 0·I-17 NONE·동일 키 202를 재현했다. 10초 추가 관측도 동일했다.
- [x] F2-01도 exit 137·재기동 뒤 PROCESSING·주문 0·배송 PICKUP_WAITING 1·활성 구간 2·I-17 ACTIVE·동일 키 202를 재현했다. 10초 관측과 최종 O-07·DB 검증도 동일했다. [결과·명령·증거](../reviews/order-creation-recovery/2026-10-20-baseline-results.md).
- [x] 전용 프로젝트만 down했다. 전용 pgdata 볼륨과 전체 원문은 보존했고 시작 전 실행 컨테이너 12개 ID는 동일했다. 서비스 소스·설정 변경과 복구 기능 구현은 없다. 실험 도구·문서는 로컬 미커밋이다.
- [x] 2026-10-20 사용자 요청으로 [장애 지점 F1·F2와 상태별 복구 판단표](../reviews/2026-10-20-order-creation-recovery-plan.md)를 작성했다. 코드 기준으로 NONE·ACTIVE·CLOSED를 구분하고, 원래 작업 차단·배송 조건부 종료·복구 이력을 제안했다. 현재 I-13의 무조건 삭제로는 출발과 복구의 경쟁을 안전하게 처리할 수 없음을 확인했다.
- [x] [실험 기록 안내와 실제 조사 명령](../reviews/order-creation-recovery/README.md), [실행별 양식](../reviews/order-creation-recovery/runs/TEMPLATE.md)을 만들었다. 계획과 실행, 기대와 실제, 코드·실환경 검증을 구분한다. F1/F2·R1~R8은 계획이며 환경 기동·장애 주입·기능 구현은 미실행이다.
- [x] 2026-10-20 고도화 상담: 현재 코드·ADR·열린 이슈를 대조해 주문 생성의 서버 종료 후 안전한 복구를 1순위로 추천했다. [기존 질문 노트](../learning/junhyun/qna/2026-10-16-backend-portfolio-hardening.md)를 현재 상태로 보강했다. 설계 채택·구현은 진행하지 않았다. PR #24의 문서·에러 코드 검사는 현재 모두 SUCCESS다. 상담 문서 변경은 로컬 미커밋이다.
- [x] 2026-10-20 dev `e55b2a3`에서 브랜치를 만들고 origin/dev와 동일한 시작점임을 확인했다.
- [x] 기존 변경을 확인했다. 학습 문서·측정 원문·이미지·임시 모니터링 구성과 관련 인계·리뷰 문서를 포함한다.
- [x] Eclipse 개인 설정(`.project`, `.settings/`, `.classpath`, `.factorypath`)과 `bin/` 빌드 산출물은 커밋 대상에서 제외하고 로컬에 보존한다.
- [x] `./gradlew build` 통과(39초, 58개 작업 중 9개 실행·49개 UP-TO-DATE). order 테스트 469개 재실행, 실패·오류·skip 0이다. 다른 모듈 테스트는 기존 결과를 재사용했다.
- [x] `script/docs-check.sh`, `script/error-codes-check.sh`(31개), `git diff --cached --check` 통과. Markdown 12개 상대 경로·JSON 1개·SVG 1개 검사와 스테이징 비밀키 패턴 검사도 통과했다.
- [x] 자료 커밋 `9c750a8`을 origin/feat/docs-learning-evidence에 푸시했다. [PR #24](https://github.com/logistics-group3/logistics-management/pull/24)를 dev 대상으로 만들고 OPEN·head와 원격 커밋 일치를 확인했다.
- [x] PR 범위의 `script/docs-check.sh origin/dev`, `git diff --check origin/dev...HEAD`도 통과했다. YAML 자료 3개 파싱을 추가로 확인했다.
- 면접 포인트: 과거 측정 증거와 현재 재검증 결과를 어떻게 구분하는가. 테스트 DB 연결과 서비스 런타임 HikariCP 메트릭은 왜 별도로 보는가.

## 다음 단계
1. PR #27의 GitHub 검사·리뷰를 확인한다. 팀 리뷰와 병합은 별도 판단으로 남긴다.
2. 복구 API·V7·실행권 계약의 의견을 반영한다. 추가 검증은 보존한 전용 DB와 새 runId로 수행한다.
3. 자동 재생성·복구 UI·자동 lease 회수·소유권 이전은 후속 범위다. IDE 설정·bin·환경 파일과 기존 문서 PR #24를 보존한다.

## 직접 해보기
1. IntelliJ에서 `OrderService.accept`와 `process`를 열고 진행 기록 커밋·I-09·주문 저장의 순서를 본다. 설계 문서의 F1·F2가 각각 그 사이에 있는지 확인한다.
2. `rg -n 'deleteByOrderId|lockOrder|softDelete' delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryService.java`로 I-13에 배송 상태 조건이 없는지 확인한다. 실제 삭제 호출은 하지 않는다.
3. 실험 실행 전 `docs/reviews/order-creation-recovery/runs/TEMPLATE.md`를 새 runId 파일로 복사하고, 가설과 기대 결과부터 채운다. 아직 실행하지 않은 명령은 계획으로 남긴다.

기존 PR 확인 절차:
1. `git diff --stat origin/dev...HEAD`로 PR에 포함된 파일을 확인한다. IntelliJ에서는 Git 로그에서 origin/dev와 현재 브랜치를 비교한다. 문서와 자료만 보이면 범위가 맞다.
2. `script/docs-check.sh origin/dev`, `script/error-codes-check.sh`를 실행해 두 검사 모두 통과하는지 확인한다.
3. `gh pr view --json url,baseRefName,headRefName,state`로 대상 dev, 현재 브랜치, OPEN 상태를 확인한다.

## 메모
- 최종 산출물 검사: 변경·추가 Markdown 39개 링크 누락 0, diff 공백·문서 동기화·에러 코드 31개 검사 통과. 구현 증거 ZIP 무결성·생성 비밀값/JWT/Slack 토큰 패턴 검사 통과. 코드·문서는 PR #27에 발행했다.
- 재현 도구 검사: Python 3개 구문·JDI Java 컴파일·diff 공백·문서 동기화·에러 코드 31개 검사 통과. 변경 Markdown 13개 링크 누락 0건, 생성 비밀값·JWT 패턴 노출 0건. 전체 정제 증거 ZIP은 결과 보고서에 연결했다.
- 실험 단계에서는 서비스·DB·실제 프로세스 재기동을 검증했다. Docker bootJar는 테스트 제외 빌드이며, 이번 단계에서 전체 Gradle 테스트·프런트·브라우저 E2E는 실행하지 않았다. 이전 설계 단계의 미실행 표시는 당시 이력이다.
- 설계 문서 검사(2026-10-20): `git diff --check`, `script/docs-check.sh` 통과. 변경 Markdown 6개 상대 링크 누락 0건. 기능 테스트 결과가 아니며 설계 문서는 로컬 미커밋이다.
- 기존 학습 문서의 2026-10-16·19 검증 결과는 당시 기록이다. 이번 빌드·정적 검사 결과와 구분한다.
- 프런트·소비 API 계약 변경이 없어 프런트 검증과 E2E는 이번 작업에서 실행하지 않는다.
- 임시 Grafana 구성은 정적 자료로 검토한다. 이번 작업에서 모니터링 스택을 띄우거나 DB 연결 고갈 실험을 재실행하지 않는다.
