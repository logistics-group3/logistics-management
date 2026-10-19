# 실제 운영을 가정한 프로젝트 리뷰

- 기준일: 2026-10-19, 브랜치 `feat/infra-monitoring-mentor-guide`, HEAD `be0b5ed`.
- 결론: 배송 상태의 동시 변경, 지연 생성의 종료 보장, 배송정보 접근 정책을 먼저 보강해야 한다. 정상 주문 시연과 운영 중 데이터 정합성 보장은 구분한다.
- 범위: 현재 소스·마이그레이션·SA·Compose·모니터링·CI 설정, 별도 PostgreSQL 16 테스트 컨테이너에서 실행한 재현 5건.
- 변경: 서비스 코드·설정·SA는 수정하지 않았다. 기존 미커밋 학습 문서와 progress 내용은 보존했다.
- 한계: 실제 운영 배포·트래픽·외부 방화벽·별도 CI·백업 서비스는 확인하지 않았다. 아래 P1은 실제 운영 전 우선 해결, P2는 부하·장애 대응을 위한 보강을 뜻한다.

## 우선순위와 확인 수준

| ID | 우선순위 | 발생 가능한 문제 | 근거 수준 |
|---|---|---|---|
| R01 | P1 | 도착·재배정, 취소·출발이 서로 덮어써 배송 상태가 모순됨 | PostgreSQL·현재 서비스 코드로 2건 재현 |
| R02 | P1 | 일반 가입자가 다른 업체의 배송지·수령인을 조회함 | SA·조회 코드·응답 DTO 확인, 기존 통합 테스트의 허용 정책 확인 |
| R03 | P1 | 배송담당자 지정을 해제해도 기존 구간 변경 권한이 남음 | `deliveryManager=false` 서비스 호출로 재현 |
| R04 | P1 | 삭제 완료 뒤 지연 생성된 배송이 남고 복구가 운영자 수동 확인에 의존함 | 순서 역전 1건 재현, 복구 API 미구현은 코드·계약 확인 |
| R05 | P2 | 업체 삭제와 상품 생성이 겹쳐 삭제된 업체에 활성 상품이 남음 | PostgreSQL·현재 서비스 코드로 1건 재현 |
| R06 | P2 | 브라우저·Gateway·내부 재시도의 시간 예산이 달라 결과 불명확 요청이 늘어남 | 설정·호출 순서 확인, 지연 부하 시험 미실행 |
| R07 | P2 | 서버 종료·큐 포화·전송 실패 때 주문 Slack 알림을 잃음 | 커밋 후 메모리 이벤트·비동기 큐 코드 확인 |
| R08 | P1 | 공개 로그인·가입 요청이 CPU·DB 연결을 소모함 | 저장소에 요청 제한 없음, 공격·부하 시험 미실행 |
| R09 | P2 | 인스턴스가 발견 목록에서 빠지면 상태 패널에서 사라지고 장애 통보도 없음 | Eureka SD·instant up 쿼리·알림 설정 부재 확인 |
| R10 | P1 | 호스트·DB 장애 때 전체 서비스가 멈추고 복원 가능 여부가 불명확함 | 저장소의 단일 호스트 Compose·DB 볼륨 확인, 실제 복원 미검증 |
| R11 | P2 | 주문이 몰리면 같은 담당자에게 배정이 집중됨 | 최소 건수 조회 후 개별 메모리 선택 확인, 대량 동시 배정 미재현 |
| R12 | P2 | 테스트가 깨지는 변경이 PR 검사에서 걸러지지 않음 | 저장소 workflow가 문서·에러 코드 검사만 실행 |

## R01. 배송 상태의 동시 변경이 보호되지 않는다

**관찰:** 명령용 조회는 일반 `findById`이고 `Delivery`·`DeliveryRoute`·상위 `BaseEntity`에 버전 필드가 없다. 같은 배송을 변경하는 요청들이 동일 상태를 읽고 검증을 각각 통과할 수 있다.

별도 트랜잭션 두 개의 읽기·쓰기 순서를 래치로 제어해 다음 결과를 확인했다. 원격 hub·user 조회는 대역이며 저장·조회·커밋은 실제 PostgreSQL과 현재 JPA 코드다.

| 재현 | 실행 순서 | 최종 관찰값 |
|---|---|---|
| 도착과 재배정 | 재배정 트랜잭션이 MOVING을 읽음 → 도착 커밋 → 재배정 커밋 | 배송 `DELIVERED`, 마지막 구간 `MOVING` |
| 취소와 집하 시작 | 출발 트랜잭션이 WAITING을 읽음 → 취소 커밋 → 출발 커밋 | 두 호출 모두 성공. 배송 `PICKING_UP`, 집하 `MOVING`, 마지막 구간 `CANCELED` |

배송이 완료됐다는 응답 뒤 완료가 풀리거나, 취소 성공으로 안내한 물건을 출발시킬 수 있다. 주문 서비스에서 주문 행을 잠그는 것으로 배송 서비스의 경쟁까지 막히지 않는다.

**개선 제안:** 동일 배송의 출발·도착·취소·원복·재배정·삭제가 공유하는 동시성 경계를 둔다. 첫 구현 후보는 배송 루트 행을 잠근 뒤 구간을 읽고 변경하는 방식이다. 낙관적 잠금을 선택하면 자식만 변경하는 재배정도 충돌을 감지하도록 버전 범위를 설계하고 충돌을 409로 변환한다. `@DynamicUpdate`만으로 상태 검증의 경쟁을 해결하지 않는다.

**완료 기준:** 두 재현에서 모순 상태가 남지 않고 충돌한 요청 하나가 거절된다. 다른 배송은 독립 진행한다. 잠금을 도입하기 전 재배정의 user 원격 조회를 긴 DB 잠금 밖으로 옮길지도 검토한다.

근거: [DeliveryService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryService.java), [DeliveryJpaRepository](../../delivery-service/src/main/java/com/sparta/logistics/delivery/infrastructure/persistence/DeliveryJpaRepository.java), [DeliveryRoute](../../delivery-service/src/main/java/com/sparta/logistics/delivery/domain/DeliveryRoute.java). 기존 협업 요청 DELIVERY-001·004에 해당한다.

## R02. 일반 사용자의 배송정보 접근 범위가 넓다

**확인:** `isScopedToOwnRoutes`는 배송담당자에게만 범위를 제한한다. 일반 USER는 전체 검색과 타 배송 단건 조회가 가능하며 응답에 집하지·배송지·수령인 이름·수령인 Slack ID가 들어간다. UUID를 알아야만 가능한 단건 공격이 아니라 목록 API로 찾을 수 있는 정책이다.

현재 SA 6.8은 일반 USER 전체 조회를 명시한다. 구현의 SA 위반이 아니라 **실제 운영으로 전환할 때 바꿔야 할 접근 정책**이다. 가입 승인 절차 없이 USER를 만드는 현재 가입 흐름과 결합하면 영향이 커진다.

**개선 제안:** 사용자와 공급·수령 업체의 관계, 배정된 구간, MASTER 여부로 조회 범위를 정한다. 목록과 상세에서 필요한 개인정보를 구분한다. 일반 USER→타 업체 배송, 담당자→타 담당 배송, 해제된 담당자→과거 배송을 API 수준에서 검사한다. SA·서비스 계약 변경을 먼저 팀과 합의한다.

근거: [DeliveryQueryService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryQueryService.java), [DeliverySummaryResult](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/dto/DeliverySummaryResult.java), [SA](../SA/sparta-logistics-sa.md). 기존 요청 DELIVERY-006에 해당한다.

## R03. 담당자 지정 해제와 기존 배정 권한이 연결되지 않는다

후속: 2026-10-19 사용자 확인 후 [T07](../../delivery-service/docs/tasks/T07.md)에서 D-03·D-05 현재 자격 검사와 프런트 조건을 로컬 보강했다. 아래 재현은 수정 전 기준으로 보존한다. 60초 캐시 지연·MASTER 수동 인계·별도 #12 조회 정책을 구분한다.

**재현:** 배송 구간의 기존 managerId와 같은 사용자에게 `Role.USER`, `deliveryManager=false`를 주어 `changeRouteStatus`를 호출했다. 403 없이 구간이 `MOVING`으로 바뀌었다.

U-07은 사용자 플래그를 해제하지만 기존 WAITING·MOVING 구간을 정리하지 않는다. D-03·D-05는 현재 배송담당자 여부 대신 managerId 일치만 검사한다. 조회 제한도 해제된 사용자는 R02의 일반 USER 규칙을 적용받는다. Gateway 캐시가 갱신돼도 이 서비스 인가 문제는 남는다.

**개선 제안:** 진행 중 업무가 있으면 해제를 거절하거나, 재배정을 완료한 뒤 해제하는 정책을 정한다. 상태 변경에도 현재 자격을 검사한다. 정지·탈퇴·허브 이동까지 같은 업무 인계 기준을 적용한다. 퇴사 당일 즉시 차단이 필요하면 현재 60초 인증 캐시의 지연도 별도로 다룬다.

근거: [DeliveryService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryService.java), [UserService](../../user-service/src/main/java/com/sparta/logistics/user/application/UserService.java). 기존 요청 DELIVERY-007에 해당한다.

## R04. 지연 생성과 삭제의 순서 역전 뒤 안전하게 종료하는 경로가 없다

**재현:** 배송 생성이 허브 경로 조회에서 대기하는 동안 같은 orderId의 `deleteByOrderId`를 먼저 끝냈다. 삭제는 대상이 없어 정상 반환했고, 대기를 해제하자 활성 배송이 만들어졌다. 삭제 명령을 반복해도 현재는 생성 이전에 종료 표시를 남기지 않는다.

주문 측은 이 위험을 인지하고 `RECOVERY_REQUIRED`를 남긴다. 그러나 배송을 orderId로 확인하는 운영용 읽기 계약, 생성·취소 진행 조회·복구 API, 장기 미해결 건 알림은 없다. 원격 성공 후 프로세스가 종료되면 PROCESSING으로 남는 경우도 수동 확인 대상이다.

**개선 제안:** 배송 측 종료 표시 또는 시도 토큰으로 과거 생성 명령의 효력을 제어한다. 동시 생성·삭제가 같은 기준으로 직렬화되도록 설계해야 하며 단순 존재 확인만 추가하면 다시 경쟁한다. 운영자는 진행 ID·예상 상태·조치 사유로 결과 확인과 복구를 수행하고 이력을 남기게 한다. 오래됐다는 이유만으로 PROCESSING을 풀고 재주문하지 않는다.

**완료 기준:** 생성 지연·삭제 선행·프로세스 종료·재요청을 거쳐도 활성 고아 배송이 없고, 복구 대상이 운영 화면에서 확인·종료된다. 이번 재현은 delivery 서비스의 순서 역전까지이며 전체 주문 HTTP 장애 시험은 수행하지 않았다.

근거: [DeliveryCreationService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryCreationService.java), [OrderService](../../order-service/src/main/java/com/sparta/logistics/order/application/OrderService.java), [주문 실패 처리](../services/order/failure-handling.md). 기존 요청 DELIVERY-002에 해당한다.

## R05. 업체 삭제와 상품 생성이 경쟁한다

후속: 2026-10-19 사용자 확인 후 [#15 구현·검증](2026-10-19-issue-15-verification.md)에서 같은 업체의 생성·삭제 잠금을 추가했다. 아래 재현은 수정 전 기준으로 보존한다.

**재현:** 상품 생성 트랜잭션이 업체를 읽은 뒤 대기하게 했다. 다른 트랜잭션에서 업체 삭제를 끝내고 상품 생성을 계속하니, 삭제된 업체 아래 `deleted_at IS NULL`인 상품 1건이 커밋됐다. 두 작업 모두 현재 서비스 메서드를 사용했고 FK는 물리 행의 존재만 확인하므로 논리 삭제를 막지 않았다.

**개선 제안:** 업체 삭제·상품 생성이 같은 업체 잠금 또는 동등한 조건부 쓰기 규칙을 공유하도록 한다. 상품 검색에서 삭제된 업체를 제외하는 방어도 두되 저장 불변식을 대체하지 않게 한다. 현재 테스트는 DB 잔존까지 확인했으며 해당 상품의 전체 공개 API 노출은 검증하지 않았다.

근거: [CompanyService.delete](../../company-service/src/main/java/com/sparta/logistics/company/application/CompanyService.java), [ProductService.create](../../company-service/src/main/java/com/sparta/logistics/company/application/ProductService.java). SA 7.5의 업체·상품 동시 논리 삭제 조건에 대한 경쟁이다.

## R06. 요청 전체의 시간 예산과 장애 전파 제한이 부족하다

**확인:** 프런트 공통 요청은 20초에 중단하고 Gateway 응답 제한은 60초다. 공통 Feign은 연결 5초·읽기 20초, 멱등 호출은 최대 3회다. 주문 생성은 업체·상품 조회와 배송 생성, 그 안의 hub·user 조회를 이어서 실행하고 실패 뒤 삭제 보상까지 할 수 있다.

사용자는 실패를 본 뒤에도 서버에서 배송·주문 처리가 계속될 수 있다. 기존 멱등키와 프런트의 동일 요청 재확인은 도움이 되지만 지연과 복구 대기를 해소하지는 않는다. 호출 지연이 쌓이면 요청 스레드가 점유되며, O-06 삭제와 배송 재배정은 원격 호출 동안 DB 트랜잭션도 유지한다.

**개선 제안:** 단계별 최대 시간·재시도·보상 시간을 합쳐 전체 마감 시간을 정한다. 장기 처리가 필요한 경우 진행 조회 계약과 비동기 접수를 검토한다. 하위 서비스별 동시 호출 상한과 빠른 실패를 검토하고, 쓰기 호출은 결과 불확실성을 그대로 추적한다. 시간 제한 숫자만 늘리는 것으로 해결됐다고 보지 않는다.

근거: [frontend API](../../frontend/src/shared/api.ts), [공통 설정](../../config-repo/application.yml), [Gateway 설정](../../config-repo/gateway-service.yml), [IdempotentRetryConfig](../../common/src/main/java/com/sparta/logistics/common/feign/IdempotentRetryConfig.java).

## R07. 주문 알림은 전달을 보장하지 않는다

**확인:** 주문 커밋 후 이벤트를 메모리 비동기 큐에 제출한다. 실행기 스레드는 2개·큐는 100개이며 제출 거부와 HTTP 실패는 로그에 남기고 끝낸다. 서버가 커밋 직후 종료되거나 대기 작업이 사라지면 재전송 근거가 없다.

알림이 참고용이면 명시적으로 수용할 수 있다. 현장 업무 시작의 필수 수단으로 쓰면 주문은 있지만 담당자가 모르는 상황이 된다.

**개선 제안:** 필수 알림은 주문과 같은 트랜잭션에 발송 대기 내역을 저장하고 별도 실행기가 재전송한다. 수신자·시도·실패·최종 결과를 조회하게 한다. Outbox를 도입해도 외부 Slack 응답 유실 때 정확히 한 번 전달되는 것은 아니므로 중복 허용·식별 정책을 함께 정한다.

근거: [OrderCreatedEventListener](../../order-service/src/main/java/com/sparta/logistics/order/application/event/OrderCreatedEventListener.java), [OrderNotificationConfig](../../order-service/src/main/java/com/sparta/logistics/order/infrastructure/config/OrderNotificationConfig.java), [SlackOrderNotificationSender](../../order-service/src/main/java/com/sparta/logistics/order/infrastructure/notification/SlackOrderNotificationSender.java).

## R08. 공개 로그인·가입에 요청량 제한이 없다

**확인:** 로그인·가입은 공개 경로다. 로그인은 존재하지 않는 계정도 BCrypt 비교를 실행하며 읽기 트랜잭션 안에 있다. 계정 노출 방어에는 도움이 되지만 대량 요청에는 CPU와 DB 연결 점유가 발생할 수 있다. 저장소의 Gateway·user 코드·설정에는 계정/IP 요청 제한을 찾지 못했다.

**개선 제안:** 공개 인증 경로부터 IP·계정 기준 제한과 429 응답을 적용한다. 전체 요청량과 인증 실패를 분리해 관측하고, 비밀번호 비교 중 DB 연결을 계속 유지할 필요가 있는지도 검토한다. 사용자 존재 여부를 드러내는 오류 차이를 만들지 않는다. 외부 프록시·WAF의 별도 방어 유무와 실제 처리량은 미확인이다.

근거: [AuthService](../../user-service/src/main/java/com/sparta/logistics/user/application/AuthService.java), [Gateway 설정](../../config-repo/gateway-service.yml).

## R09. 장애가 대시보드에서 사라질 수 있다

**확인:** Prometheus는 Eureka에서 발견한 인스턴스만 수집한다. 서비스 상태 패널은 현재 `up`을 instant로 표시한다. 마지막 인스턴스까지 발견 목록에서 빠지면 `up=0`이 계속 남는다고 보장할 수 없고 패널 항목이 사라질 수 있다. 저장소에는 Alert rule·Contact point·Alertmanager 설정이 없다.

**개선 제안:** 기대하는 서비스 목록과 현재 발견된 서비스 목록을 비교하고, 인스턴스 0개·시계열 부재·오류율·응답 지연·장기 미해결 주문을 감시한다. 로그가 쌓이는 것과 담당자가 장애를 통보받는 것을 구분한다. 같은 호스트의 Prometheus/Grafana가 함께 죽는 경우를 확인할 외부 점검도 둔다. 실발송·복구 통보는 별도 승인된 테스트 수신처로 검증한다.

근거: [Prometheus 설정](../../infra/prometheus/prometheus.yml), [상태 패널](../../infra/grafana/dashboards/logistics-overview.json), [Compose](../../docker-compose.yml). 운영 UI에서 별도로 만든 알림의 존재는 확인하지 않았다.

## R10. 현재 Compose만으로 운영하면 호스트·DB 장애의 영향이 크다

**확인:** 단일 Compose 네트워크에 Gateway·Eureka·서비스·PostgreSQL 1개가 있고 서비스별 스키마를 같은 DB 서버에 둔다. DB는 로컬 볼륨에 저장하며 저장소에 별도 백업·복원 실행 절차는 없다. Zipkin과 Prometheus 데이터는 컨테이너 재생성 시 보존되지 않는 개발용 구성이다.

**개선 제안:** 운영에서 허용하는 데이터 유실 시간과 복구 시간을 먼저 정하고 외부 백업·별도 환경 복원 시험을 수행한다. 같은 DB 장애가 전체 서비스에 미치는 영향을 기준으로 운영 DB와 호스트 구성을 결정한다. HTTPS 종료 지점, 내부 API의 네트워크 경계·서비스 인증, 자원 제한, 배포 중 준비 상태 확인도 운영 구성에서 검증한다. 로컬 Compose의 제한된 포트 공개는 확인했으므로 모든 내부 포트가 인터넷에 노출됐다고 판단하지 않는다.

현재 구성은 개발용이라는 문서 의도를 따른다. 별도 운영 인프라·백업·프록시가 실제로 없다는 주장은 아니다.

## R11. 동시 배정의 편차가 1~2건으로 제한되지 않는다

**확인:** 미완료 건수를 읽은 다음 요청별 `ManagerAssigner` 메모리에서 최솟값·UUID 순으로 선택한다. 동시에 같은 건수를 읽는 요청은 같은 후보를 고를 수 있고 읽기와 배정 커밋을 묶는 조정이 없다. 코드 주석의 1~2건 편차는 보장된 상한이 아니다.

**개선 제안:** 먼저 동시 배정 수·최대 편차·담당자 업무 용량을 측정한다. 필요하면 허브별 배정 직렬화나 예약 건수의 원자적 증가를 적용한다. 단순 구간 개수는 실제 거리·시간·근무 여부를 반영하지 않는다는 모델 한계도 구분한다.

근거: [DeliveryCreationService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryCreationService.java), [ManagerAssigner](../../delivery-service/src/main/java/com/sparta/logistics/delivery/domain/ManagerAssigner.java). 실제 대량 동시 부하는 미실행이다.

## R12. 저장소 CI가 코드 검증을 수행하지 않는다

**확인:** `.github/workflows/`의 workflow는 문서 동기화와 에러 코드 검사만 실행한다. 백엔드 build·프런트 build/lint/test가 병합 전 자동 검증되는 저장소 정의는 없다. 실제 GitHub branch protection·외부 CI는 조회하지 않았다.

**개선 제안:** Gradle build와 frontend 필수 검증을 별도 job으로 실행한다. DB 통합 테스트가 PostgreSQL 컨테이너를 쓰도록 하고, 이번 재현은 결함을 고친 뒤 안전 상태를 단언하는 회귀 테스트로 전환한다.

근거: [docs-guard workflow](../../.github/workflows/docs-guard.yml), [전체 빌드 안내](../guides/full-build.md).

## 검증 기록

1. 다음 명령으로 배송 4건·업체 1건의 진단 테스트를 실행했다. `BUILD SUCCESSFUL`, 실패·오류·skip 0이다. **결함이 발생한 관찰값을 단언해 재현 성공을 확인한 것이며 안전성 테스트 통과를 뜻하지 않는다.**

   ```sh
   ./gradlew -I .local/reviews/2026-10-19/review.init.gradle :delivery-service:test --tests com.sparta.logistics.delivery.integration.OperationsReviewReproductionTest :company-service:test --tests com.sparta.logistics.company.integration.OperationsReviewReproductionTest --console=plain
   ```

   최초 실행은 `/tmp/logistics-ops-review.ifACYK/review.init.gradle`을 사용했다. 같은 재현 코드·init script·JUnit XML을 `.local/reviews/2026-10-19/`로 옮겨 로컬 증거로 보존했다. 이 경로는 `.gitignore` 대상이며 커밋 자료가 아니다.

2. 진단 소스 주입 없이 `./gradlew :delivery-service:test :company-service:test :gateway-service:test --console=plain`을 실행했다. 배송 기존 테스트 39개·업체 기존 테스트 46개를 재실행했고 실패·오류·skip 0이다. Gateway task는 `UP-TO-DATE`여서 기존 27개 보고서를 재사용했으며 이번 새 실행으로 세지 않는다.
3. 전체 `./gradlew build`, 프런트 npm 검증, 실제 Gateway·JWT를 통과하는 재현, 강제 프로세스 종료, 부하·Slack 발송·백업 복원 시험은 실행하지 않았다. 서비스·프런트 코드는 변경하지 않았다.
4. `git diff --check`, `script/docs-check.sh`(매핑된 코드 변경 없음), `script/error-codes-check.sh`(29개 중복·누락 없음)를 통과했다. 보고서의 상대 파일 링크도 존재 여부를 확인했다.

## 권장 진행 순서

후속: 2026-10-19 사용자 요청으로 아래 핵심 6개를 [GitHub 이슈와 수정 계획](2026-10-19-operations-fix-plan.md)에 등록했다. 사용자는 간단한 것부터 상세 설명을 읽고 확인한 뒤 한 항목씩 수정하기로 했다. 첫 제안은 #13(R03)이며 아직 코드 수정 전이다. 아래는 최초 리뷰의 위험 우선순위로 보존한다.

1. R01의 배송 동시 변경 회귀 테스트를 정식 테스트로 옮기고 같은 배송의 쓰기를 보호한다.
2. R02·R03의 조회·담당자 해제 정책을 팀과 합의하고 API 인가 테스트를 붙인다. 공개 배포 전 R08도 적용한다.
3. R04의 배송 종료 계약과 주문 진행 조회·복구를 하나의 시나리오로 완성한다. 지연 요청과 재시작 뒤 최종 정합성을 증명한다.
4. R05의 업체·상품 불변식을 보호하고 R06 시간 예산을 정한다.
5. R09·R10·R12로 장애 통보·복원·병합 검증을 갖춘다. 필수 알림이면 R07을 함께 처리한다.

재고·결제·분실·재배송 등은 현재 SA의 필수 범위와 구분한다. 이번 결과를 근거로 기능을 추가하거나 팀 설계를 변경하기로 확정한 것은 아니다.

## 공식 참고 자료

- [Hibernate 동시성 제어](https://docs.hibernate.org/orm/6.6/userguide/html_single/#locking): 버전·잠금 방식의 일반 근거다. 실제 결함의 근거는 위 PostgreSQL 재현이다.
- [AWS Transactional Outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html): DB 변경과 발행할 이벤트를 함께 저장하는 패턴이다.
- [Prometheus Alerting](https://prometheus.io/docs/practices/alerting/): 사용자 영향·모니터링 자체의 장애를 감시하는 기준이다.
