# feat/infra-monitoring-mentor-guide

## 지금 하는 일
운영 개선 이슈 6개 등록 완료, #13 구현은 feat/delivery-T07에서 진행

## 진행 상태
- [x] 2026-10-19 사용자 요청으로 핵심 운영 위험 R01~R06을 GitHub #11~#16으로 등록했다. gh로 생성 후 6개 OPEN·제목·본문·URL을 다시 읽어 확인했다. [수정 순서와 #13 상세 계획](../reviews/2026-10-19-operations-fix-plan.md)을 작성했다. 코드·테스트·프런트·설정·SA는 아직 바꾸지 않았다.
- [x] #13: 2026-10-19 사용자가 상세안을 확인하고 구현을 요청했다. 범위는 D-03·D-05의 현재 담당자 자격 검사, 프런트 버튼 조건, 회귀 테스트, 계약 문서다. 60초 캐시의 즉시 무효화와 자동 인계는 포함하지 않는다. [T07 progress](feat-delivery-T07.md)에서 이어간다.
- [x] 2026-10-19 [실제 운영 리뷰](../reviews/2026-10-19-operations-review.md)를 작성했다(HEAD `be0b5ed`). 별도 PostgreSQL 16 컨테이너에서 도착·재배정 경쟁, 취소·출발 경쟁, 해제된 담당자 상태 변경, 삭제 선행 뒤 지연 생성, 업체 삭제·상품 생성 경쟁 5건을 재현했다. 진단 소스·JUnit XML은 `.local/reviews/2026-10-19/`에 보존했다. 서비스·설정·SA는 변경하지 않았다. 배송 기존 테스트 39개·업체 46개 재실행은 실패·오류·skip 0이고 Gateway 27개는 UP-TO-DATE 보고서 재사용이다. 전체 build·프런트·실제 Gateway E2E·부하·Slack 발송·복원 시험은 미실행이다.
- [x] 2026-10-18 사용자 요청으로 열려 있던 티스토리 편집기에 TIL 10일차 본문을 입력하고 임시저장했다. 기존 제목과 `백엔드 심화 TIL [내일배움캠프]` 카테고리를 유지했다. HTML → 기본모드·미리보기에서 상위 섹션 7개, 표 5개, 코드 블록 3개, 업로드 이미지 1개, 일기·회고 빈칸 2개를 확인했다. UI의 `임시저장 개수 1개`를 확인했고 발행하지 않았다.
- [x] 2026-10-16 [TIL 10일차 초안](../learning/junhyun/blog/2026-10-16-observability-and-cross-hub-order.md)과 [하루 정리](../learning/junhyun/daily/2026-10-16.md)를 작성했다. 기존 TIL 9일차 원문·오늘 Notion 회의록·스텝 47~51·현재 코드·기존 테스트 보고서를 대조했다. JUnit XML 766개, 최종 E2E 18개 결과를 재확인했고 기존 양식의 상위 섹션 7개·일기/회고 빈칸 2개·문서 링크를 검사했다. `git diff --check`, `script/docs-check.sh` 통과. 글 작성 중 빌드·E2E는 재실행하지 않았다.
- [x] dev `8e9ebe6`(PR #8 병합 후)에서 브랜치 생성. 사용자 요청: Grafana·Prometheus 연동, 프런트에 멘토 테스트 가이드·절차·테스트 데이터, 로컬의 미커밋 작업도 함께 올린다
- [x] `9f64e3c` 로컬 작업 커밋: Zipkin 질문 노트 2개와 학습 노트 목록
- [x] `de2f6e0` Prometheus·Grafana 연동(ADR 0018, 스텝 49)
  - 서비스 메트릭 노출, Gateway 관리 포트 8090 분리, Feign 메트릭 이름 분리
  - Eureka SD 수집, Grafana 대시보드 프로비저닝
  - 검증: `./gradlew build` 734개 통과(새 테스트 5개). 개발·UI 테스트 스택 모두 수집 대상 6개 UP, 공개 포트 메트릭 401
- [x] `f1032a7` Grafana 서비스 상태 패널을 instant 쿼리로 바꿔 사라진 인스턴스가 DOWN으로 남지 않게 함
- [x] `d20ef84` 멘토 테스트 가이드 `/guide`, 로그인 화면 계정 입력, 시나리오 시드 데이터, E2E(스텝 50)
  - 프런트 build·lint·format, 단위 테스트 46개 통과
  - E2E: 기존 15개 통과(허브 선택 조건 수정 후), 가이드 2개 통과(색 대비 수정 후). 수정 뒤 17개를 한 번에 다시 돌리지는 않았다
- [x] 문서: `docs/guides/monitoring.md`, `docs/guides/mentor-test.md`, 보안 규칙 S4·S5, 로컬 실행, README들. `script/docs-check.sh` OK
- [x] 검증 환경 복구
  - `.env.ui-test`의 `SLACK_DM_ENABLED`를 원래 값(true)으로 되돌렸다(백업과 동일 확인). UI 테스트 스택은 작업 전처럼 내렸다
  - 개발 스택을 원래 테스트 모드(`API_TEST_SQL=DEBUG script/api-test.sh up`)로 다시 올리고 prometheus·grafana를 띄웠다. 수집 대상 6개 up
  - UI 테스트 스택을 다시 띄우면 실제 DM이 켜진 상태라 `seed`·E2E가 거부된다. 멘토용 시연 전에는 DM을 끄고 `up`을 다시 실행한다
- [x] `36c6219` origin/dev 병합(사용자 요청): hub PR #9의 경로 272건 시드(V3)와 H-03·H-04·I-03을 가져왔다. 충돌 없음
  - 검증: `./gradlew build` 766개 통과(hub 62개 포함), `script/docs-check.sh origin/dev` OK
  - 개발 스택(Slack 꺼짐)에서 hub-service를 다시 빌드했고 Flyway V3가 적용됐다
  - `GET /api/v1/hub-routes` 200
  - HUB_A → HUB_B 주문 201. 배송은 집하·허브 간 이동(예상 52.13km·45분)·업체 배송 3구간이고 모두 자동 배정됐다. 확인용 주문은 취소·삭제했다
- [x] 멘토 가이드·프런트 갱신(사용자 동의, 스텝 51)
  - `CreateOrder.tsx`의 다른 허브 주문 제한을 풀고 3구간 안내로 바꿨다
  - 가이드의 경로·발제 예시 시나리오를 실제 동작으로 바꾸고 "구현 대기"를 없앴다. 관련 문서와 ADR 0015 결과 절을 갱신했다
  - 검증: 프런트 build·lint·format·단위 46개 통과. E2E 18개 통과(경기 → 부산 주문·3구간·담당자별 처리·삭제 포함)
  - 실패한 시도에서 남은 테스트 주문 3건은 정리했다. 검증 뒤 `.env.ui-test` DM 원복, UI 테스트 스택 내림, 개발 스택 테스트 모드 복구
- [x] 배송 로직 검토 요청 DELIVERY-004~008을 delivery 협업 로그에 추가(`ae2f039`). 근거는 질문 노트 2026-10-16-delivery-actual-distance-review
- [x] 2026-10-16 사용자 요청으로 푸시하고 dev 대상 PR #10을 만들었다(https://github.com/logistics-group3/logistics-management/pull/10). 리뷰 요청: y0y00700, kej-2356, ChoiSeungKeun, jihaneol
- [ ] 리뷰 반영과 병합(squash). 리뷰 포인트: actuator 노출(S4), Gateway 관리 포트 8090, ADR 0018·0015 채택, 시드가 E2E에 주는 영향, DELIVERY-004~008
- 면접 포인트: 메트릭·로그·추적의 차이, Pull 방식 수집과 서비스 디스커버리, RED 지표, 같은 이름 메트릭의 라벨 키 충돌, 관리 포트 분리와 인증 우회 방지

## 다음 단계
2026-10-19 사용자 요청의 현재 순서는 [수정 계획](../reviews/2026-10-19-operations-fix-plan.md)을 따른다. #13은 사용자 확인을 받아 feat/delivery-T07에서 구현한다. 다음 항목은 설명 후 별도 확인을 받는다.

운영 개선에 착수하면 [리뷰의 권장 순서](../reviews/2026-10-19-operations-review.md#권장-진행-순서)에 따라 배송 동시 변경 보호부터 진행한다. 개인정보 조회·담당자 해제 정책과 배송 지연 종료 계약은 담당자와 SA 합의 후 구현한다. 이번 요청은 리뷰이며 구현 선택은 확정하지 않았다.

1. PR #10 리뷰 의견을 반영한다. ADR 0018·0015 상태는 팀 결정에 따라 바꾼다
2. 허브 관리 화면에서 경로를 조회하는 UI는 아직 없다(가이드는 Swagger로 안내). 필요하면 허브 상세에 경로 목록을 붙인다
3. 필요하면 Grafana Alerting → Slack(질문 노트 2026-10-16-slack-monitoring-alerts)을 별도 ADR로 검토한다

## 직접 해보기
운영 리뷰 확인: IntelliJ에서 `.local/reviews/2026-10-19/README.md`의 재현 명령과 두 `OperationsReviewReproductionTest.java`를 읽는다. `staleReassignmentOverwritesArrival`의 두 트랜잭션 순서와 최종 `DELIVERED`·`MOVING` 단언을 확인한다. 테스트가 통과하는 이유는 안전하기 때문이 아니라 결함 관찰값과 일치하기 때문이다. 상세 보고서의 R01·R04를 현재 서비스 코드와 비교한다.

1. `script/run.sh`로 다시 빌드해 띄우고 http://localhost:9090/targets 에서 6개 `UP`, gateway가 `:8090`인지 본다.
2. http://localhost:3000 에서 "스파르타 물류 서비스 개요"가 열리는지 본다. 주문을 하나 만들고 30초 뒤 "주문 생성 응답"이 늘었는지 확인한다.
3. `curl -i http://localhost:8080/actuator/prometheus`가 401인지 확인한다.
4. 멘토 환경: `script/ui-test.sh up` → `seed` → `frontend` 후 http://127.0.0.1:5174/guide 를 연다. 대전 롤백 시나리오를 "uimaster로 시작"으로 실행해 404 안내와 주문 수 유지를 확인한다.
5. 같은 화면에서 uidrvgg로 바꿔 서울 배송 상세가 403인지 본다.
6. 블로그 초안의 일기·회고를 직접 채운다. IntelliJ에서 `docs/learning/junhyun/blog/2026-10-16-observability-and-cross-hub-order.md`를 열고, `rg -n '^## |직접 작성' docs/learning/junhyun/blog/2026-10-16-observability-and-cross-hub-order.md`로 기존 섹션 순서와 작성할 곳을 확인한다. 검증 수치는 스텝 51과 기존 테스트 보고서의 범위에 맞는지 대조한다.

## 메모
- 2026-10-16 물류 시뮬레이션 상담: [가속 시간·AI 사건·게임 결합 제안](../learning/junhyun/qna/2026-10-16-logistics-simulation-game.md)을 기록했다. 480배속 가상 시간, 실제 API를 호출하는 실행기, AI 사건 검증, 운영자 대응과 재생을 추천했다. 채팅 개념 시연과 설계 제안이며 서비스 코드·SA 계약은 변경하지 않았다.
- 시뮬레이션 후속 제안: 허브 간 정상 연동을 완성한 뒤 소수 주문·가상 시계·배송 구간 진행·지연 사건·기록부터 만든다. IntelliJ에서 `Delivery.changeRouteStatus`, `RouteStatus`, `RouteInfo`를 읽고 기존 상태와 신규 사고 효과의 차이를 확인한다. 면접 포인트는 가상/실제 시간 분리와 사건 재현성이다.
- 2026-10-16 보안·장애 알림 상담: [보안 고도화 우선순위](../learning/junhyun/qna/2026-10-16-security-hardening-priorities.md)와 [Slack 운영 알림 구성](../learning/junhyun/qna/2026-10-16-slack-monitoring-alerts.md)을 기록했다. 코드·SA·공식 문서를 읽고 데이터 조회 정책, 로그인 제한, 내부 호출 인증을 검토했으며 후속 질문에는 Grafana Alerting → Slack을 추천했다. 팀 설계 변경과 신규 기능 채택은 확정하지 않았다.
- 상담 검증 범위: 코드·설정 정적 확인과 질문 노트 작성이다. 빌드·장애 주입·Slack 실발송·현재 모니터링 기동은 수행하지 않았다. 다른 작업에서 진행 중인 코드·설정 변경은 이 상담의 구현 성과가 아니다.
- 상담 직접 확인: `rg -n 'whitelist|user-cache-ttl|expiration-seconds' config-repo`로 인증 설정을 찾고 IntelliJ에서 `DeliveryQueryService`를 SA 8.4와 비교한다. `rg --files infra/grafana`와 `cat infra/prometheus/prometheus.yml`로 수집·알림 설정 파일을 구분한다. 알림·복구 수신은 별도 전용 환경 검증 과제로 남는다. 면접 포인트는 인증과 인가, 지표 수집과 장애 알림의 차이다.
- 2026-10-16 고도화 상담 완료: HEAD `9f64e3c`의 코드·설계와 AWS·k6 공식 문서를 읽고 [취업 포트폴리오 고도화 우선순위](../learning/junhyun/qna/2026-10-16-backend-portfolio-hardening.md)를 기록했다. 지연 생성/삭제 역전 복구와 장애 실험을 우선 추천했으며 SA 변경·신규 기능 채택은 확정하지 않았다. 이 상담에서는 소스 변경·런타임 실험을 수행하지 않았다.
- 고도화 상담 직접 확인: `rg -n 'handleUnknownDelivery|후속 설계' order-service/src/main/java docs/services/order/failure-handling.md`로 현재 처리와 미구현 범위를 찾는다. IntelliJ에서는 `DeliveryCreationService.create`와 `DeliveryService.deleteByOrderId`를 나란히 열어 생성·삭제 시점을 읽고 DELIVERY-002와 비교한다. 정적 확인이며 장애 재현 성공을 뜻하지 않는다.
- 고도화 상담의 다음 제안: 허브 간 정상 주문의 연동을 확인한 뒤 지연 생성·삭제 역전의 회귀 시나리오부터 설계한다. 면접 포인트는 타임아웃과 실패의 차이, 멱등성과 명령 순서의 차이, 안전한 복구 종료 근거다. 기존 모니터링·멘토 가이드 작업 항목은 유지한다.
- 별도 개념 질의 기록(2026-10-16): [Gateway의 비공개 관리 포트](../learning/junhyun/qna/2026-10-16-gateway-private-management-port.md)를 설명했다. Gateway 화이트리스트·보안 코드·Compose 매핑을 읽었으며 로그인 등은 JWT 예외임을 확인했다. 질문 응답에서 코드·설정을 변경하지 않았고 관리 포트 기동·수집·외부 차단은 검증하지 않았다.
- 2026-10-16 병합 전 발제 대조 결과: 당시 허브 간 경로(H-03·H-04·I-03)와 경로 272건 적재가 없어 테스트 UI는 같은 허브 주문만 허용했다(ADR 0015). 이후 dev 병합과 [스텝 51](../learning/junhyun/steps/step-51-cross-hub-order-ui.md)에서 이 제한을 해소했다. 현재 상태는 위 진행 상태의 허브 간 주문 연동 항목을 따른다.
- 2026-10-16 작성 당시 기술블로그는 로컬 초안이었다. 기존 블로그 RSS에서 양식을 다시 확인했고, 당시에는 티스토리에 입력·임시저장·발행하지 않았다. 2026-10-18 사용자 요청으로 본문 입력·임시저장까지 진행했다(위 진행 상태). 그림은 2026-10-16 E2E의 테스트 배송 완료 화면 원본이다.
- 티스토리 입력 증거: `/Users/alloy/.codex/visualizations/2026/10/16/01a144a4-9f43-7682-bcae-41cdf27b541d/tistory-til10-draft-20261018.png`. 편집기는 기본모드로 열어 두었다. 글의 대상 작업일·제목은 2026-10-16이고 티스토리 입력일은 2026-10-18이다.
- 발제문 사본: 팀 Notion "발제문 원본"
