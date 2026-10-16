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
