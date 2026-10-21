---
title: 허브 간 이동 도착 때 실제 거리·소요시간을 왜 필수로 받나, 그리고 배송 로직 검토
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 중급
tags: [delivery, domain-model, validation, concurrency, optimistic-lock, privacy, msa]
---

# 허브 간 이동 도착 때 실제 거리·소요시간을 왜 필수로 받나, 그리고 배송 로직 검토

## 질문
실제 거리와 소요시간을 왜 필수로 입력해야 하나. 배송 쪽 로직을 자세히 검토해 보자.

## 짧은 답
- 발제가 정한 것이 아니라 팀 SA의 결정이다. 발제는 배송 경로 기록에 "예상/실제 거리 및 소요시간" **필드를 두라**고만 했다.
- SA 6.8 D-05가 "허브 이동 구간을 ARRIVED로 바꿀 때 필수, 다른 구간은 선택"으로 정했다. 예상값(p_hub_routes)이 있는 구간이 허브 이동뿐이라, 계획과 실적을 비교할 수 있는 곳에서만 실제값을 강제한 것으로 읽힌다(SA에 이유는 적혀 있지 않다).
- 하지만 지금 방식은 목적을 이루지 못한다.
  - **소요시간:** 시스템이 잴 수 있는데 사람이 손으로 입력한다.
  - **0km·0분도 통과한다.** 형식만 필수다.
  - **다른 구간:** 거리만 넣는 반쪽 값도 저장된다.
- 소요시간은 출발·도착 시각을 기록해 자동으로 계산하는 편이 맞다. 거리는 입력한다면 0보다 커야 하고, 거리·시간을 함께 받도록 바꾸는 것을 제안한다. 배송 담당자와 팀이 SA를 고쳐 결정할 일이다.

## 자세히

### 1. 지금 규칙이 있는 곳

| 위치 | 규칙 |
|---|---|
| SA 6.8 D-05(`docs/SA/sparta-logistics-sa.md:2199-2201`) | `actualDistanceKm`·`actualDurationMinutes`는 허브 이동 구간 ARRIVED 때 필수, 다른 구간은 선택. 누락이면 400 `VALIDATION_ERROR` |
| `DeliveryRoute.arrive()` | `HUB_TRANSFER`이고 둘 중 하나라도 없으면 400. 다른 구간은 받은 값을 그대로 저장 |
| `ChangeRouteStatusRequest` | 두 값 모두 `@PositiveOrZero`. 0은 허용 |
| `DeliverySheet.tsx` `needActual` | 허브 이동 + 도착일 때만 입력칸을 보여 주고 `required` |
| `p_delivery_routes`(V1) | `actual_*` 컬럼만 있고 출발·도착 시각 컬럼은 없다. 값 CHECK도 없다 |

### 2. 실제로 시험한 결과(2026-10-16, 개발 스택, 허브 간 주문 1건, 끝난 뒤 삭제)

| 요청 | 결과 |
|---|---|
| 허브 이동 ARRIVED, 값 없음 | 400 VALIDATION_ERROR "허브 이동 구간 도착에는 실제 거리와 소요시간이 필요합니다." |
| 허브 이동 ARRIVED, 거리만 | 400 |
| 허브 이동 ARRIVED, 거리 -1 | 400 |
| 허브 이동 ARRIVED, **0km·0분** | **200, 저장됨**(예상 52.13km·45분 옆에 실제 0.0km·0분) |
| 업체 배송 ARRIVED, **거리 5km만** | **200, 거리 5.0·시간 null로 저장** |
| 집하 ARRIVED, 값 없음 | 200 |

### 3. 무엇이 문제인가
- **소요시간을 잴 수 있는데 사람이 입력한다.** 구간은 API로 MOVING(출발) → ARRIVED(도착)를 거친다. 두 시각을 저장하면 소요시간은 계산된다. 하지만 구간에 출발·도착 시각이 없다. `updated_at`은 매번 덮어써져 쓸 수 없다. 그래서 담당자가 아무 숫자나 넣어도 확인할 방법이 없다.
- **필수인데 의미 없는 값이 통과한다.** `@PositiveOrZero` 때문에 0km·0분이 허용된다. 실제로는 "칸을 채웠는가"만 검사한다.
- **값 객체의 약속이 깨진다.** `RouteInfo`는 "거리와 시간은 항상 함께 다루는 값"이라고 적혀 있다. 그런데 허브 이동이 아닌 구간은 반쪽 값을 저장한다.
- **화면 부담이 생긴다.** 도착 버튼을 누르는 순간 두 숫자를 직접 입력해야 한다. 예상값을 미리 채워 주지도 않는다.

### 4. 바꾼다면
1. `p_delivery_routes`에 `departed_at`, `arrived_at`을 추가한다(V2 마이그레이션). `start()`와 `arrive()`에서 시각을 기록한다.
2. 실제 소요시간은 `arrived_at - departed_at`으로 계산해 저장한다. 요청에서 받지 않는다.
3. 실제 거리는 선택으로 둔다. 받는다면 0보다 크게(`@Positive`) 받는다. 계획과 차이가 큰 값(예: 예상의 3배 이상)을 경고할지는 정책으로 정한다.
4. 모든 구간에서 거리·시간을 둘 다 받거나 둘 다 비우게 한다(`RouteInfo` 불변식).
5. SA 6.8 D-05와 프런트 `needActual`을 같이 고친다. delivery는 다른 팀원 담당이라 delivery 협업 로그에 요청으로 남긴다.

### 5. 배송 로직 검토 결과(2026-10-16 dev `18b6aa5` 기준 코드)

| 순위 | 내용 | 근거 | 영향 |
|---|---|---|---|
| 높음 | **동시 변경 보호 장치가 없다.** `@Version`도 행 잠금도 없다 | `grep @Version` 0건, `DeliveryRepository.findById`에 잠금 없음 | 아래 두 경우 모두 기록 결과가 실제 진행과 어긋난다 |
| 높음 | 그 예 1: 주문 취소(I-11)와 집하 출발(D-05)이 동시에 오면 둘 다 `PICKUP_WAITING`을 읽고 각자 커밋한다 | `Delivery.cancel`, `startRoute` | 주문은 취소됐는데 구간은 이동 중일 수 있다. 문서에 미해결로 적혀 있다(order `integration-contract.md`) |
| 높음 | 그 예 2: 담당자 재배정(D-04)과 도착 처리(D-05)가 동시에 오면, 늦게 커밋한 재배정이 구간 행 전체를 이전 상태(MOVING)로 덮어쓸 수 있다 | `DeliveryRoute.changeManager`는 도착·취소만 막고, Hibernate는 바뀐 Entity의 모든 컬럼을 UPDATE한다 | 배송은 "도착"인데 구간은 "이동 중"으로 되돌아간다 |
| 중간 | **일반 사용자가 모든 배송의 주소·수령인·수령인 Slack ID를 본다** | `DeliveryQueryService.isScopedToOwnRoutes`는 배송담당자만 범위를 좁힌다. 요약 DTO에 `recipientSlackId`가 있다 | 발제의 "모든 로그인 사용자 조회"는 지키지만 개인정보가 넓게 노출된다. 최소한 Slack ID는 응답에서 빼는 것을 검토한다 |
| 중간 | 실제 거리·시간 문제(위 3절) | 시험 결과 2절 | 계획 대비 실적 데이터를 믿을 수 없다 |
| 중간 | 배송담당자 지정을 해제하면 맡은 구간이 그대로 남는다 | D-05 권한은 `route.managerId`만 비교. 해제된 사람은 조회 범위 제한도 풀린다 | 진행 중인 구간이 처리할 사람 없이 남거나 해제된 사람이 계속 처리한다. 해제할 때 재배정 안내나 차단이 필요하다 |
| 낮음 | 배송 검색에 동점 해소용 정렬 기준이 없다 | `DeliveryQueryRepositoryImpl.search` orderBy가 요청 정렬뿐이다(주문은 ID로 보조 정렬) | `createdAt`이 같으면 페이지 사이에 중복·누락이 생길 수 있다 |
| 낮음 | 자동 배정이 동시 주문을 반영하지 못한다 | `ManagerAssigner` 주석에 감수한다고 적혀 있다 | 편차 1~2건. 의도된 선택 |
| 낮음 | 테스트 공백: D-01~D-05의 HTTP 계층 테스트가 없다. 통합 테스트가 I-03을 가짜로 바꾼다 | `DeliveryIntegrationTest`의 `@MockitoBean HubRouteReader` | `@RequireRole`·요청 검증, hub 응답 형식 변화를 빌드가 잡지 못한다. hub I-03이 생겼으니 계약 테스트를 추가할 수 있다 |
| 참고 | 늦게 도착한 I-09가 보상 삭제 뒤 배송을 만드는 고아 배송 | DELIVERY-002 OPEN | 이미 협업 로그에 있다 |

잘 된 점도 있다.
- 구간 상태는 애그리거트를 통해서만 바뀐다. 배송 상태는 구간 상태에서 매번 계산하므로 두 값이 따로 놀지 않는다.
- 생성은 원격 호출을 트랜잭션 밖에서 한다. `orderId` 유니크로 같은 주문의 중복 생성을 막는다(멱등).
- 담당자가 없으면 저장 전에 실패한다. 그래서 주문 Saga의 롤백 시나리오가 깔끔하게 성립한다.

### 6. 동시 변경을 막는 방법
- **낙관적 잠금:** `Delivery`와 `DeliveryRoute`에 `@Version`을 둔다. 먼저 커밋한 쪽이 이기고, 늦은 쪽은 `OptimisticLockException` → 409로 응답한다. 사용자는 화면을 새로 고쳐 다시 시도한다. 충돌이 드물고 사람이 누르는 API라 적합하다.
- **비관적 잠금:** 명령용 조회에 `@Lock(PESSIMISTIC_WRITE)`(`SELECT ... FOR UPDATE`)를 건다. 충돌 자체가 없지만 대기가 생긴다. 원격 호출을 트랜잭션 밖에 두는 지금 구조라 잠금 시간은 짧다.
- 구간 상태가 바뀌면 `refreshStatus()`가 배송 상태도 함께 바꾼다. 그래서 `Delivery`의 버전만으로도 D-05끼리, D-05와 I-11 사이의 충돌은 잡힌다. 재배정(D-04)은 배송 행을 바꾸지 않으므로 `DeliveryRoute`에도 버전이 필요하다.

## 이 프로젝트에서는
- delivery-service는 다른 팀원(한얼)의 담당이다. 코드는 직접 고치지 않는다. `docs/services/delivery/README.md` 협업 로그에 요청(OPEN)으로 남기고 담당자가 판단한다.
- SA 6.8 D-05를 바꾸면 프런트 `DeliverySheet.tsx`의 `needActual`과 멘토 가이드의 "실제 거리·소요시간 입력" 문구도 함께 바뀐다.

## 면접 포인트
- **값을 사람에게 받을지 시스템이 계산할지:** 시스템이 관측할 수 있는 값(시각, 소요시간)은 계산한다. 관측할 수 없는 값(실제 이동 거리)만 입력받고, 입력값은 범위로 검증한다.
- **JPA에서 lost update가 생기는 이유와 막는 방법:** 변경 감지는 Entity 전체 컬럼을 UPDATE한다. 두 트랜잭션이 같은 행을 읽고 쓰면 늦은 쪽이 앞의 변경을 덮는다. `@Version`(낙관적), `SELECT FOR UPDATE`(비관적)로 막는다.
- **조회 권한과 개인정보:** "모든 사용자가 조회할 수 있다"와 "모든 필드를 보여 준다"는 다르다. 응답 DTO를 역할별로 나누는 방법이 있다.

## 참고
- 코드:
  - `delivery-service/.../domain/DeliveryRoute.java`(`arrive`, `changeManager`)
  - `Delivery.java`(`changeRouteStatus`, `cancel`, `reassignRouteManager`)
  - `DeliveryService.java`, `DeliveryQueryService.java`, `DeliveryQueryRepositoryImpl.java`
  - `ChangeRouteStatusRequest.java`, `V1__create_delivery_tables.sql`
- SA 6.8 D-05, 2.5 배송 상태 전이
- 실행: 2026-10-16 개발 스택 시험(2절), 확인용 주문은 삭제
