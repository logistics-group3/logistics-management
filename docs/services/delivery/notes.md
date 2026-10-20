# delivery 구현 노트

## T06 I-15 배송 알림 수신자 조회 (2026-10-16)

### 처리 과정
- InternalDeliveryController → DeliveryQueryService → 기존 QueryDSL 상태·구간 조회로 배송과 담당자를 읽는다. 상태를 바꾸거나 배정하지 않는다.
- WAITING·MOVING 구간의 managerId를 중복 제거한다. 완료·취소 배송은 빈 목록이고 없음·삭제 배송은 404다.

### 핵심
- 구간마다 같은 담당자가 배정될 수 있어 수신자 조회부터 중복을 줄인다. order도 이메일 단위로 한 번 더 중복을 막는다.
- 기존 I-10 응답을 유지하고 I-15에 필요한 필드만 둔다. 이메일은 user에서 읽으므로 delivery에 복제하지 않는다.
- 조회 결과 뒤의 재배정·취소까지 잠그지는 않는다. 이 조회는 발송 시점 전체의 원자성을 보장하지 않는다.

### 확인 질문
- 동일 사용자가 집하·업체 배송을 모두 맡으면 알림을 몇 번 보내야 하는가?
- 완료 배송의 빈 목록과 삭제 배송의 404는 왜 구분하는가?

### 메모
- delivery 39개 테스트와 실제 같은 허브 주문의 I-15·DM 1건을 확인했다. [T06 기록](../../../delivery-service/docs/tasks/T06.md), [ADR 0016](../../decisions/0016-delivery-manager-slack-dm.md)

기능마다 핵심만 남긴다. 상태·배정 규칙의 설명은 [`delivery.md`](../../../delivery-service/docs/domain/delivery.md), 요청·응답은 [`delivery-api.md`](../../../delivery-service/docs/domain/delivery-api.md), 작업 범위와 커밋 기록은 [`tasks/`](../../../delivery-service/docs/tasks/README.md)에 있다.

## T01 상태 전이와 저장소 (2026-10-15)
### 처리 과정
- `Delivery`(애그리거트 루트)에 `create`, `changeRouteStatus`(`startRoute`/`arriveRoute`), `cancel`, `revertCancel`, `updateRecipient`, `reassignRouteManager`, `softDelete`를 두었다. `DeliveryRoute`의 변경 메서드는 package-private이다.
- 배송 상태는 `DeliveryStatus.from(routes)`가 구간 상태에서 계산한다. 마지막으로 진행된 구간이 MOVING이면 그 유형의 이동 상태, ARRIVED면 다음 구간 유형으로 대기 상태가 정해진다.
- `DeliveryRepository`(domain) ← `DeliveryJpaRepository` + QueryDSL 조각 `DeliveryQueryRepositoryImpl`.
### 핵심
- `routes`의 cascade는 PERSIST만 둔다. 이후 변경은 변경 감지로 반영되고 물리 삭제가 없으니 REMOVE·orphanRemoval이 필요 없다. 논리 삭제는 `Delivery.softDelete`가 구간까지 처리한다.
- 같은 허브 2구간에서 집하 도착이 `DEST_HUB_ARRIVED`가 되는 예외를 따로 분기하지 않는다. "다음 구간이 업체 배송이면 목적지 허브 도착"이라는 규칙 하나로 3구간·2구간이 함께 처리된다.
### 확인 질문
- 배송 상태를 컬럼에 저장하면서도 구간에서 다시 계산하는 이유는 무엇인가?
- `setStatus()`를 열어 두면 어떤 버그가 생길 수 있나?

## T02 I-09 배송 생성과 담당자 자동 배정 (2026-10-15)
### 처리 과정
- `InternalDeliveryController.create` → `DeliveryCreationService.create`: orderId 조회(있으면 200) → I-03(허브가 다를 때) → 구간 담당 허브마다 I-14 → 후보 담당 건수 한 번에 조회 → I-01(수령인 Slack ID) → `Delivery.create`(`ManagerAssigner`로 구간별 배정) → `saveAndFlush`.
- Feign: `UserClient`, `HubClient`(모두 GET, `IdempotentRetryConfig`), 어댑터가 배송 용어(`DeliveryManager`, 수령인)로 바꾼다.
### 핵심
- 생성 서비스에는 `@Transactional`이 없다. 원격 호출을 트랜잭션 밖에서 하고, 동시 요청의 `order_id` 유니크 위반을 잡은 뒤 새 조회로 먼저 저장된 배송을 돌려주기 위해서다. 저장 자체는 Spring Data의 `saveAndFlush` 트랜잭션 하나에서 배송과 구간이 함께 들어간다.
- 후보가 없는 허브가 있으면 저장 전에 404 `DELIVERY_MANAGER_NOT_FOUND`라 남는 데이터가 없다.
- 같은 배송 안의 앞선 배정을 담당 건수에 더해 센다(집하·허브 이동이 한 사람에게 몰리지 않게). 동점은 userId 문자열 순이다(`UUID.compareTo`는 부호 있는 비교라 쓰지 않는다).
- 수령인 I-01이 404면 Slack ID 없이 진행한다. 503·타임아웃은 그대로 올려 order가 보상한다.
- 허브 단위 advisory lock은 두지 않았다. 동시 주문에서 담당 건수 편차가 1~2건 생길 수 있다.
### 확인 질문
- 생성 메서드에 `@Transactional`을 붙이면 동시 요청 중 하나가 500이 되는 이유는?
- I-14를 구간마다가 아니라 허브마다 부르는 이유는?

## T03 배송 조회 D-01, D-02, I-10 (2026-10-15)
### 핵심
- 배송담당자(MASTER 아님)의 조회 범위는 검색 조건(`scopeManagerId`)에 넣어 DB에서 거른다. "담당 구간이 있는 배송"은 조인 대신 EXISTS라 행이 중복되지 않고 count가 맞다.
- 목록 응답에는 구간이 없어 구간을 읽지 않는다(N+1 없음). 단건은 구간을 지연 로딩으로 한 번 더 읽는다.
- 정렬 허용 필드는 `createdAt`, `status`다(`QuerydslSupport.toOrderSpecifiers`).

## T04 재배정 D-04, 구간 상태 변경 D-05, 배송지 수정 D-03 (2026-10-15)
### 핵심
- D-04는 `@RequireRole(Role.MASTER)`. I-01로 배송담당자인지 확인하고(아니면 404), 소속 허브 ≠ 구간 담당 허브면 400, 도착·취소 구간이면 409. 허브·상태 검사는 Entity가 한다.
- D-05는 그 구간 담당자 본인 또는 MASTER만 된다. 다른 구간의 담당자도 403이다.
- D-03은 업체 배송 구간 담당자 본인 또는 MASTER, 업체 배송 출발 후면 409.

## T05 취소·원복·삭제 I-11, I-12, I-13 (2026-10-15)
### 핵심
- 취소는 `PICKUP_WAITING`에서만, 이미 취소면 그대로 200. 원복은 취소 상태일 때만 바꾸고 그 밖에는 no-op 200.
- I-13 삭제자는 `AuditorAware`(order가 전달한 `X-Username`, 없으면 `SYSTEM`)로 남긴다. 내부 호출은 사용자 헤더가 없을 수 있어 `@CurrentUser`를 쓰지 않는다.

## T07 D-03·D-05 현재 배송담당자 자격 검사 (2026-10-19)

### 처리 과정
- Controller → DeliveryService에서 배송·대상 구간을 읽고 validateAssignedManager로 현재 자격과 배정 ID를 함께 확인한다. D-03은 마지막 구간, D-05는 요청한 구간을 검사한다.
- MASTER 또는 현재 배송담당자인 해당 구간 담당자만 Entity 변경으로 넘어간다. 그 외에는 기존 FORBIDDEN(403)이고 DB 값은 유지된다.
- 프런트 nextRouteAction도 같은 조건으로 출발·도착 버튼을 정한다. 추가 원격 조회·DB 변경은 없다.

### 핵심
- 배정 ID는 지정 해제 후에도 남으므로 ID 일치만으로 현재 업무 권한을 판단하면 안 된다.
- MASTER는 배송담당자 플래그가 false여도 기존 상태 규칙 안에서 처리한다.
- Gateway 캐시 지연은 최대 60초다. 즉시 폐기 대신 현재 자격이 전달된 뒤의 차단을 보장한다.
- 진행 중 구간은 MASTER가 D-04로 수동 인계한다. 조회 정책 #12와 자동 인계는 별도 범위다.

### 확인 질문
- 해제 후 managerId가 같다는 이유만으로 허용하면 어떤 권한이 남는가?
- /users/me의 false 응답은 왜 Gateway 캐시가 false라는 증거가 아닌가?

### 메모
- 사용자 확인을 받은 #13 범위의 로컬 구현이다. 팀 전체 승인·병합 완료는 아니다.
- 테스트·환경 복구 결과 정본: [T07](../../../delivery-service/docs/tasks/T07.md). 처음에 서비스·HTTP 4건과 프런트 2건의 실패를 확인했다. 배송 61개·프런트 48개와 실제 Gateway 회귀 단독 실행을 통과했다.

## #11 배송 변경 동시 실행 보호 (2026-10-19)
### 처리 과정
- 변경 명령(D-03·D-05, I-11·I-12·I-13)은 `findByIdForUpdate`(또는 `findByOrderIdForUpdate`)로 배송 행을 잠그고 읽는다. 잠금은 서비스의 `@Transactional`이 끝날 때 풀린다.
- D-04는 `@Transactional`을 떼고 I-01 조회 → `TransactionOperations.execute`(잠금 → 재배정) 순서로 처리한다.
### 핵심
- 낡은 상태 검사가 문제였으므로 "먼저 잠그고 나서 읽는다"가 핵심이다. 잠금 전에 읽은 Entity는 잠금 뒤에도 낡은 채로 남는다(영속성 컨텍스트가 같은 인스턴스를 돌려준다). 그래서 D-04의 사전 `findById`를 없애고 잠금 조회 한 번으로 읽는다.
- 잠금 범위는 배송 행 하나다. 구간은 `Delivery`를 통해서만 바뀌므로 구간 변경도 보호된다. 삭제된 행은 `@SQLRestriction`과 PostgreSQL의 재평가로 잠금 대기 뒤 조회되지 않아 404가 된다.
- 테스트는 서비스 호출을 테스트의 바깥 트랜잭션 안에서 해 커밋을 미루고(읽고 바꾼 뒤 멈춤), 그 사이 끼어드는 요청이 끝나는지 기다리는지로 잠금을 판별한다. 서비스의 `@Transactional`이 바깥 트랜잭션에 합류하는 성질을 이용했다.
### 확인 질문
- `@Transactional`만으로 두 요청의 상태 경쟁을 못 막는 이유는?
- 비관적 잠금과 `@Version` 중 이 문제에 비관적 잠금을 고른 이유는? 반대로 낙관적이 나은 경우는?
- D-04에서 user-service 호출을 잠금 안에 두면 어떤 문제가 생기나?

## #14 종료된 생성 시도의 지연된 I-09 차단 (2026-10-19)
### 처리 과정
- I-13: 같은 orderId의 advisory lock → 종료 표시 INSERT(`ON CONFLICT DO NOTHING`) → 배송이 있으면 논리 삭제. 대상이 없어도 표는 남는다.
- I-09: 기존 배송 확인 → 종료 표시 확인(원격 호출 전) → hub·user 조회(잠금 없음) → 짧은 트랜잭션에서 잠금 → 기존 배송·종료 표시 재확인 → 저장.
- I-17: 활성 배송이면 ACTIVE, 종료 표시가 있으면 CLOSED, 둘 다 없으면 NONE.
### 핵심
- "확인한 뒤 저장"은 확인과 저장 사이에 다른 요청이 끼면 소용없다. 그래서 확인과 저장을 같은 잠금 아래에 둔다. I-13의 잠금과 같은 키라 서로 직렬화된다.
- 잠글 행이 아직 없는 경우(생성 전)는 행 잠금이 아니라 advisory lock을 쓴다. 키는 UUID 문자열의 해시이며, 해시가 겹쳐도 정합성은 같고 서로 다른 주문이 잠시 기다릴 뿐이다.
- 원격 호출(최대 20초)은 잠금 밖이다. 잠금 구간은 DB 조회·저장 몇 번이다.
- NONE은 종료의 증거가 아니다. 원래 I-09가 아직 실행 중일 수 있다는 정보를 그대로 전달한다.
### 확인 질문
- 종료 표시만으로 "확인 후 저장 사이에 삭제가 끼는" 순서를 막지 못하는 이유는?
- 행 잠금 대신 advisory lock을 쓴 이유는? 단점은?
- I-17이 NONE일 때 "종료됐다"고 판단하면 안 되는 이유는?

## T06 배송 E2E 스크립트 (2026-10-15)
### 처리 과정
- E2E는 `script/delivery-e2e-check.sh`, 시드는 `infra/postgres/seed/delivery-e2e.sql`, 실행 방법은 [`e2e.md`](../../../delivery-service/docs/e2e.md).
### 핵심
- 단위·통합 테스트는 hub·user·order를 목으로 바꿔 서비스 사이 연결을 확인하지 못한다. 고정 ID 시드로 Gateway를 거쳐 주문 → 배송 완료 → 삭제를 한 번에 확인한다.

## I-18 주문 생성 복구용 조건부 종료 (2026-10-20)

### 처리 과정
- Delivery.closeCreation에서 집하·모든 구간 대기·예상 ID를 검증하고 DeliveryService에서 orderId → 배송 행 잠금으로 조율한다.
- 배송 없음·이미 종료는 종료 표시를 보존하고 204다. 진행·취소·ID 불일치는 배송과 종료 표시 모두 유지하며 거절한다.
- I-13을 바꾸지 않고 별도 내부 API를 추가했다. [T08](../../../delivery-service/docs/tasks/T08.md), [ADR 0019](../../decisions/0019-order-creation-operator-recovery.md)를 따른다.

### 핵심
- 출발과 정리가 같은 배송 행을 잠가 관찰 뒤 상태 변경 경쟁을 막는다. 조건 검사·구간 논리 삭제·종료 표시가 같은 트랜잭션이다.
- 생성 저장도 같은 orderId 잠금을 사용하므로 종료 표시 이후 늦은 I-09를 거절한다.

### 확인 질문
- I-13에 조건을 바로 추가하면 어떤 기존 호출자가 영향을 받는가?
- 출발 선행과 정리 선행 각각의 올바른 결과는 무엇인가?

### 메모
- PostgreSQL에서 두 경쟁 순서·구간 삭제·반복 종료·ID 불일치를 확인했다. 실제 F1/F2 결과는 [전후 보고서](../../reviews/order-creation-recovery/2026-10-20-implementation-results.md)를 따른다.
