# 43. 주문 취소 API와 진행 기록

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `6ac9b8a feat(order): 주문 요청사항 수정 API 구현`
- 이번 변경: O-05 코드·V2·테스트·문서, 미커밋

## API 계약

```http
POST /api/v1/orders/{orderId}/cancel
Authorization: Bearer {accessToken}
```

본문은 없다. 주문자 본인 또는 MASTER만 취소할 수 있고 성공 응답은 다음과 같다.

```json
{
  "status": 200,
  "message": "주문이 취소되었습니다.",
  "data": {"orderId": "취소한 주문 UUID", "status": "CANCELED"}
}
```

없음·삭제는 404, 타인 주문은 403이다. 이미 취소됐거나 집하가 시작됐으면 409다. 취소 진행 중·복구 대기인 경우도 409로 새 취소를 막는다.
배송 결과가 불확실하면 503, 로컬 저장·복구 실패는 500이다. 상세 분류와 응답 뒤 실제 상태는 [예외 처리 설계](../../../services/order/failure-handling.md)에 있다.

## 만든 것과 역할

| 위치 | 역할 |
|---|---|
| Order.validateCancelable·cancel | ORDERED → CANCELED 전이 |
| OrderCancellation·Status·FailureReason | 진행·완료·중단·복구 대기와 사유 보관 |
| V2__create_p_order_cancellations.sql | 주문별 미해결 시도 하나를 보장하는 부분 유니크, 상태 CHECK, 같은 스키마 주문 FK |
| OrderCancellationRepository·JpaRepository | 시도 저장·잠금 조회·미해결 여부 확인 |
| OrderCancellationWriter | 짧은 로컬 트랜잭션과 커밋 결과 재확인 |
| OrderCancellationService | 배송 취소·주문 완료·실패 보상 조율 |
| DeliveryCancelClient·CancelRevertClient·Adapter | I-11·I-12 실제 Feign, 서로 다른 재시도 정책 |
| CancelOrderResult·Response, OrderController | 명세의 취소 결과 변환과 POST 연결 |

## 왜 이렇게 만들었는가

**1. 주문 상태만 CANCELED로 바꾸면 배송은 계속 진행될 수 있다.** 그래서 배송을 먼저 취소한다. 배송과 모든 구간의 변경은 delivery가 책임지고, 주문은 I-11의 취소 성공을 확인한 뒤 상태를 바꾼다.
배송 조회 GET 결과는 확인 이후 바뀔 수 있으므로 I-11이 집하 상태 확인과 취소를 원자적으로 수행하도록 계약을 따른다.

**2. DB 트랜잭션을 나눠도 취소 전체의 중복 실행은 막아야 한다.** 두 요청이 동시에 배송을 취소하고 그중 하나만 저장에 실패하면, 그 요청의 원복이 다른 요청의 성공을 되돌릴 수 있다.
이를 막기 위해 원격 호출 전 진행 기록을 먼저 커밋한다. 사용자에게 구체적인 ADR을 제시했고 "진행 기록 추가 적용" 승인을 받은 뒤 새 테이블을 추가했다.

**3. 진행 기록은 실패 후에도 남아야 한다.** PROCESSING과 RECOVERY_REQUIRED는 같은 주문에 하나만 존재한다. 완료·중단 기록은 보존하면서 새 시도를 허용할 수 있다.
메모리에만 잠금을 두면 재시작 후 결과를 잃는다. 자동 만료를 두면 아직 진행 중인 원격 요청과 새 취소가 다시 겹칠 수 있으므로 시간만으로 해제하지 않는다.

**4. 원격 호출과 로컬 트랜잭션을 구분한다.** 실행권 획득, 주문 완료, 실패 기록은 각각 REQUIRES_NEW 메서드로 커밋한다. 바깥 Service는 NEVER로 기존 DB 트랜잭션 안에서 호출하는 실수를 막는다.
그 결과 배송 응답을 기다리는 동안 DB 행 잠금을 유지하지 않는다. 같은 주문의 새 취소는 진행 기록으로 차단하고, 다른 주문 작업은 계속할 수 있다.
운영 공통 설정의 open-in-view=false를 통합 테스트에도 명시해 각 트랜잭션에서 최신 Entity를 읽는다.

**5. 커밋 예외가 곧 롤백은 아니다.** 주문 CANCELED와 시도 COMPLETED를 같은 커밋으로 확정한다. 예외가 발생하면 새 트랜잭션으로 이 두 상태를 확인한다.
이미 완료됐다면 200을 반환하고, 확실한 롤백이면 I-12로 배송을 원복한다. DB를 읽을 수 없거나 상태가 맞지 않으면 원복을 추측해서 실행하지 않는다.

**6. 재시도와 보상은 다른 행동이다.** I-11은 1회 호출한다. I-12는 이전 취소를 되돌리는 별도 API이며 응답 없는 통신 실패에만 최대 3회 호출한다.
I-11 타임아웃 뒤 늦게 취소가 완료될 수 있어 원복을 먼저 보내면 순서가 바뀔 수 있다. 이 경우에는 RECOVERY_REQUIRED로 남긴다.
복구 기록 갱신까지 실패해도 PROCESSING이 실행권을 계속 보존하며, 최초 오류 응답을 다른 예외로 덮지 않는다.

## 검증

- 취소 도메인 테스트를 먼저 작성해 메서드 누락 실패를 확인한 뒤 구현했다.
- 신규 61개: 도메인 12개, application 14개, PostgreSQL 17개, HTTP/Feign 18개.
- 전체 `./gradlew build --console=plain` 성공. 주문 252개, 전체 XML 350개, 실패·오류·skip 0. 다른 모듈 테스트는 기존 성공 결과를 재사용했다.
- PostgreSQL CHECK로 flush 실패, 지연 제약 트리거로 커밋 시점 실패를 재현했다. 주문·시도 완료를 함께 롤백하고 트랜잭션 밖에서 원복함을 확인했다.
- 같은 주문 중복 취소·다른 주문 독립 진행·대기 중 요청사항 변경 보존, 미해결 부분 유니크·상태 CHECK를 확인했다.
- I-11 타임아웃 뒤 지연 완료, 5xx·잘못된 성공 응답, I-12 통신 3회·5xx 1회를 실제 HTTP로 검증했다. 인증 오류 본문을 읽는 테스트 드라이버 문제는 TS-012에 기록했다.
- 격리 Gateway·JWT·Order·PostgreSQL 16에서 200·400·401·403·404·409·500·503을 확인했다. 위조 사용자 헤더 제거, 실제 Audit·배송 원복·지연 완료 후 새 취소 차단을 검증했다.
- 인증용 user와 delivery 응답은 HTTP 대역이다. 팀 delivery의 실제 구간 취소·원복과 실제 커밋 응답 패킷 유실·프로세스 강제 종료 시험은 미확인이다.
- 격리 실행 근거는 임시 `logistics-order-cancel-smoke-1gc3lz4s/result.json`과 서버 로그다. 임시 프로세스·DB 컨테이너를 정리했고 실제 Slack 메시지는 보내지 않았다.
- 로컬은 `docker compose build order-service` 후 `up -d --no-deps --no-build --wait --wait-timeout 90 order-service`로 주문만 반영했다. 이미지 일치·health UP·Flyway V2·같은 스키마 FK를 확인했다.
- Gateway OpenAPI의 O-05·본문 없음·orderId/status 응답·미인증 POST 401을 확인했다. 기존 주문 데이터 해시, 다른 8개 컨테이너 ID·시작 시각, PostgreSQL 볼륨, .env 해시·Webhook 값을 유지했다. 취소 진행 기록은 0건이며 실제 업무 데이터로 취소하지 않았다.
- 최종 전체 build와 문서·에러 코드 16개·공백·변경 범위 42개 파일의 로컬 링크 검사를 통과했다. 실제 Webhook 값이 변경 파일·최근 주문 로그에 없음을 확인했다.

## 직접 해보기

1. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderCancellation*'`를 실행한다. 61개가 통과하면 O-05 검증을 재현한 것이다.
2. IntelliJ에서는 OrderCancellationTest·RecordTest·ServiceTest·IntegrationTest·HttpIntegrationTest 클래스 옆 실행 버튼을 사용한다.
3. `OrderCancellationService.cancel`에서 writer.start → delivery.cancel → writer.complete 순서를 찾는다. 실패 경로에서는 resolveCompletion이 revertCancellation보다 먼저인지 확인한다.
4. Gateway Swagger에서 O-05와 응답의 orderId·status를 확인한다. 실제 delivery가 준비된 테스트 주문·토큰으로 호출하면 200·CANCELED, 다시 호출하면 409여야 한다.
5. 장애 시험은 운영 데이터 대신 위 통합 테스트로 재현한다. 복구 대상 확인 쿼리와 판단표는 [예외 처리 설계](../../../services/order/failure-handling.md) 6절에 있다.

## 다음 설계

사용자 추가 요청으로 생성·취소의 예외 행렬, 책임 경계, 재시도 시간 예산, 수동 복구 절차와 보강 우선순위를 문서화했다.
O-01의 커밋 결과 확인·생성 진행 기록, 외부 주문 멱등키·진행 조회, 복구 API·delivery 시도 토큰은 후속 설계이며 이번에 구현하지 않았다.

## 참고

- [ADR 0006](../../../services/order/decisions/0006-order-cancellation-progress.md), [SA 5.5·6.7·7.2](../../../SA/sparta-logistics-sa.md)
- [서비스 구현 노트](../../../services/order/notes.md), [예외 처리 설계](../../../services/order/failure-handling.md)
- [트러블슈팅 TS-012](../../../troubleshooting.md)
