# 주문 연동 담당자를 위한 실패 계약과 확인 항목

- 기준일: 2026-10-15
- 대상: company·delivery 구현자, 공통 예외 처리·Gateway 유지보수 담당자
- 정본: [SA](../../SA/sparta-logistics-sa.md) 6.3·7장. 이 문서는 현재 주문 코드의 기대 동작과 미확정 사항을 연결한다.
- 검증 범위: 주문의 실제 HTTP·Feign·PostgreSQL 테스트. 팀 company·delivery의 실제 업무 구현과의 연동은 아직 미확인이다.

## 먼저 알아야 할 원칙

**4xx 거절, 5xx, 타임아웃은 같은 의미가 아니다.** 주문은 제공 서비스의 명확한 4xx를 변경 전 거절로 취급한다.
제공 서비스가 상태를 바꾼 뒤 4xx를 반환하면 보상이 생략될 수 있으므로, 실패 시 로컬 트랜잭션을 롤백한 뒤 공통 오류 응답을 반환해야 한다.
5xx·응답 없음·잘못된 성공 본문은 변경 여부를 모르는 결과로 취급한다.

- 내부 성공 응답은 ApiResponse로 감싸지 않은 DTO다([ADR 0010](../../decisions/0010-internal-api-unwrapped-response.md)).
- 오류 응답은 공통 ErrorResponse 형식이며 code·message가 필요하다. 하위 4xx 코드는 주문 응답까지 유지된다.
- X-User-*는 공통 FeignHeaderInterceptor가 전달한다. 제공 서비스는 공통 Audit를 사용해 원래 호출 사용자를 기록한다.
- 원격 호출 자체를 로컬 DB 롤백으로 되돌릴 수 없다. 주문은 성공한 앞 단계를 별도 보상 API로 되돌린다.
- 주문에서 500·503을 반환했더라도 원격 변경이 없었다고 단정하지 않는다. 재호출 전에 진행 기록과 실제 상태를 확인한다.

## company 담당자

| API | 주문이 사용하는 계약 | 실패 시 주문 처리 |
|---|---|---|
| I-07 GET /internal/companies/{companyId} | companyId·name·hubId·address·managerId | 명확한 4xx는 그대로 반환, 배송 호출 없음 |
| I-08 GET /internal/products/{productId} | productId·companyId·name·price | 명확한 4xx는 그대로 반환, 배송 호출 없음 |

가격은 0 이상인 정수 원화다. 누락과 0원을 구분하고, 소수 가격을 조용히 잘라 쓰지 않는다. 상품 ID·소속 업체·필수 텍스트도 주문 어댑터에서 확인한다.
두 GET은 응답 없는 통신 실패에 최대 3회 호출할 수 있다. 5xx 응답은 재시도하지 않는다.
이 조회·권한 검증은 생성 진행 기록과 I-09 호출보다 앞에 있다. 거절된 조회 때문에 배송·생성 진행 기록이 만들어지지 않아야 한다.

## delivery 담당자

| API | 제공자가 보장할 계약 | 주문 측 호출 정책 |
|---|---|---|
| I-09 POST /internal/deliveries | 같은 orderId의 생성이 중복되지 않음. 성공 응답에 deliveryId | 같은 orderId로 통신 실패 최대 3회. 성공 ID 누락·본문 오류는 미확정 처리 |
| I-11 POST /internal/deliveries/{deliveryId}/cancel | 집하 시작 여부 확인과 배송·구간 취소를 한 로컬 트랜잭션으로 처리. 응답 status=CANCELED | 1회. 명확한 4xx는 종료, 미확정 결과는 복구 대기 |
| I-12 POST /internal/deliveries/{deliveryId}/cancel-revert | 취소 전 상태로 원복. 이미 원복된 같은 요청은 no-op | 통신 실패 최대 3회, 5xx는 1회. 현재 주문은 성공 응답 본문을 사용하지 않음 |
| I-13 DELETE /internal/deliveries/orders/{orderId} | 배송·구간 논리 삭제. 없음·이미 삭제도 204 | OrderService가 실패 시 총 3회 시도, Feign 자체 재시도 없음 |
| I-10 GET /internal/deliveries/{deliveryId} | deliveryId·orderId·status | 현재 주문 코드에는 미연결. 향후 삭제·복구 판단용 |

I-11이 이미 CANCELED일 때 200을 반환하는 서버 멱등성과, 주문이 자동 재시도하지 않는 정책은 별개다. 현재 호출 정책은 SA 3.5를 따른다.
I-09·I-13은 orderId 기준, I-11·I-12는 deliveryId 기준이다. 재시도마다 다른 ID를 발급하지 않는다.

### 생성에서 주의할 순서 역전

`I-09 처리 중 → 주문에서 타임아웃 → I-13이 없음으로 204 → 지연된 I-09 생성 완료` 순서가 가능하다.
따라서 주문은 SA 7.1의 I-13 보상을 시도하되, I-09 결과가 불확실하면 삭제 성공 후에도 RECOVERY_REQUIRED를 유지한다.
I-13의 204만으로 지연 I-09가 끝났다고 판단하지 않는다.

같은 orderId의 늦은 생성·재시도를 종료 표시로 차단할지, 시도 토큰으로 처리할지는 **미확정**이다. 기존 API가 이 보장까지 제공한다고 가정하지 않는다.
삭제 이력을 포함한 멱등키 수명과 요청 순서 역전 정책은 [DELIVERY-002](../delivery/README.md#delivery-002-지연-생성과-삭제의-순서-역전-방지-계약-검토)에 질문으로 남겼다.

## 주문이 보관하는 진행 기록

| 기록 | 언제 생기는가 | 완료 기준 |
|---|---|---|
| p_order_creations | 업체·상품·권한 검증 후, I-09 호출 전 | 주문 행과 COMPLETED를 같은 커밋으로 확정 |
| p_order_cancellations | 주문·권한·상태 검증 후, I-11 호출 전 | 주문 CANCELED와 COMPLETED를 같은 커밋으로 확정 |

PROCESSING은 아직 최종 결과를 기록하지 못한 상태다. RECOVERY_REQUIRED는 배송 결과·커밋 결과·보상 결과의 추가 확인이 필요하다는 뜻이다.
두 상태를 시간만으로 만료시키거나 다른 서비스가 주문 DB를 직접 갱신하지 않는다. 종료하려면 실제 상태에 대한 주문 담당자의 확인과 별도 복구 유스케이스가 필요하다.
현재 복구 API는 없으며 확인 쿼리·판단 순서는 [실패 처리 설계](failure-handling.md)에 있다.

생성 기록은 주문 행이 없어도 존재한다. 이전 버전에서 생성한 주문에 기록이 없다고 미완료 주문으로 판단하지 않는다.
외부 O-01 POST의 Idempotency-Key는 아직 도입하지 않았다. 생성 기록이 있다고 사용자 재요청의 중복 주문까지 막는 것은 아니다.

## 팀 연동 때 함께 확인할 시나리오

| 시나리오 | 확인할 결과 |
|---|---|
| 상품·업체 없음, 배송담당자 없음 | 원래 4xx 코드 유지, 변경 데이터 없음 |
| 동일 orderId로 I-09 동시 요청·타임아웃 재시도 | 하나의 배송과 같은 deliveryId |
| 집하 시작과 I-11 동시 요청 | delivery에서 한쪽 상태 전이만 성공 |
| I-11 성공 후 주문 DB 롤백 | I-12로 배송·구간이 함께 원복 |
| I-12 응답 유실 후 같은 요청 | 중복 원복 부작용 없음 |
| I-13 반복 호출, 없는 주문 ID | 204이며 다른 배송에 영향 없음 |
| I-09 응답 지연·I-13 선행 | 지연 생성 처리 정책을 확인하고 미해결 결과를 복구 대상으로 식별 |
| 주문 커밋 완료 후 반환 경계 예외 | 주문 보존, I-13 없음, 생성 이벤트 재발행 없음 |

주문 측 재현은 `./gradlew :order-service:test --tests '*OrderCreation*' --tests '*OrderHttpIntegrationTest' --tests '*OrderCancellation*'`로 시작한다.
외부 서비스 대역 테스트의 성공을 제공 서비스 실제 구현 검증으로 표시하지 않는다. 담당자의 구현·실제 연동 결과를 협업 로그에 추가한다.

## 진행 중인 협업 기록

- [COMPANY-001](../company/README.md#company-001-주문-연동용-조회-응답과-오류-계약-확인): 기존 I-07·I-08 구현·연동 확인 요청.
- [DELIVERY-001](../delivery/README.md#delivery-001-주문-saga의-내부-api와-실패-계약-연동-확인): 기존 I-09·I-11·I-12·I-13 구현·연동 확인 요청.
- [DELIVERY-002](../delivery/README.md#delivery-002-지연-생성과-삭제의-순서-역전-방지-계약-검토): 추가 정책 검토, 아직 합의되지 않음.

현재 주문 보강은 기존 SA와 대역 테스트를 기준으로 진행할 수 있다. 위 요청은 실제 서비스 연동과 추가 자동 복구 보장을 확인하기 위한 것이며, 다른 서비스의 결정으로 임의 표시하지 않는다.
