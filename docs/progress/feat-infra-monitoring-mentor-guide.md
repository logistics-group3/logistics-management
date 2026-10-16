# feat/infra-monitoring-mentor-guide

## 지금 하는 일
메트릭·멘토 가이드·허브 간 주문 연동 완료, 푸시·PR 대기

## 진행 상태
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
- [ ] 푸시·PR(사용자 요청 시). 공통 영역 변경이라 팀원 전체 리뷰
- 면접 포인트: 메트릭·로그·추적의 차이, Pull 방식 수집과 서비스 디스커버리, RED 지표, 같은 이름 메트릭의 라벨 키 충돌, 관리 포트 분리와 인증 우회 방지

## 다음 단계
1. 사용자가 원하면 푸시하고 dev 대상 PR을 만든다. 본문에 ADR 0018과 보안 규칙 S4 변경, Gateway 헬스 체크 주소 변경(8090)을 적는다
2. 허브 관리 화면에서 경로를 조회하는 UI는 아직 없다(가이드는 Swagger로 안내). 필요하면 허브 상세에 경로 목록을 붙인다
3. 필요하면 Grafana Alerting → Slack(질문 노트 2026-10-16-slack-monitoring-alerts)을 별도 ADR로 검토한다

## 직접 해보기
1. `script/run.sh`로 다시 빌드해 띄우고 http://localhost:9090/targets 에서 6개 `UP`, gateway가 `:8090`인지 본다.
2. http://localhost:3000 에서 "스파르타 물류 서비스 개요"가 열리는지 본다. 주문을 하나 만들고 30초 뒤 "주문 생성 응답"이 늘었는지 확인한다.
3. `curl -i http://localhost:8080/actuator/prometheus`가 401인지 확인한다.
4. 멘토 환경: `script/ui-test.sh up` → `seed` → `frontend` 후 http://127.0.0.1:5174/guide 를 연다. 대전 롤백 시나리오를 "uimaster로 시작"으로 실행해 404 안내와 주문 수 유지를 확인한다.
5. 같은 화면에서 uidrvgg로 바꿔 서울 배송 상세가 403인지 본다.

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
- 2026-10-16 발제 대조 결과: 허브 간 경로(H-03·H-04·I-03)와 경로 272건 적재가 없어 다른 허브 간 주문은 실패한다. 테스트 UI는 같은 허브 주문만 허용한다(ADR 0015). 멘토 가이드는 이 범위를 그대로 적는다
- 발제문 사본: 팀 Notion "발제문 원본"
