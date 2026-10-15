# 44. 주문 생성 진행 기록과 커밋 재확인

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `2e5fcc1 feat(order): 주문 취소와 실패 복구 기록 구현`
- 이번 변경: O-01 진행 기록·V3·커밋 재확인·협업 문서, 미커밋

## 바뀐 동작

POST /api/v1/orders의 요청·201 응답·정수 원화 계약은 유지한다. 생성 흐름 내부에서 실패의 실제 결과를 확인하도록 보강했다.

1. 업체·상품 조회와 주문 권한을 검증한다.
2. 생성 시도 ID·선발급 주문 ID·주문자를 PROCESSING으로 별도 커밋한다.
3. 트랜잭션 밖에서 I-09 배송 생성을 호출한다.
4. 주문 INSERT·생성 기록 COMPLETED·생성 이벤트를 한 트랜잭션에서 처리한다.
5. 저장 예외가 나면 새 트랜잭션에서 결과를 재확인한다.

| 재확인 결과 | 처리 |
|---|---|
| 주문과 COMPLETED 기록이 일치 | 저장된 결과로 201. 배송 삭제·이벤트 재발행 없음 |
| PROCESSING이며 주문 없음 | I-13 보상. 성공 ABORTED, 실패 DELIVERY_DELETE_FAILED·RECOVERY_REQUIRED |
| 상태 불일치·DB 조회 실패 | ORDER_COMMIT_UNKNOWN·RECOVERY_REQUIRED, 자동 삭제 없음 |

배송 생성 자체가 4xx로 거절되면 ABORTED다. I-09 타임아웃·5xx·잘못된 성공 응답은 기존 I-13 보상을 시도하지만, 삭제가 성공해도 DELIVERY_RESULT_UNKNOWN·RECOVERY_REQUIRED로 남긴다.
시작 기록을 커밋하지 못하면 배송을 호출하지 않는다. 종료·복구 기록 갱신 실패가 최초 HTTP 오류를 덮지 않게 했다.

## 만든 것과 역할

| 위치 | 역할 |
|---|---|
| OrderCreation·Status·CreationFailureReason | 생성 진행 상태와 복구 사유 |
| OrderCreationRepository·JpaRepository | 진행 기록 저장·orderId 기준 잠금 조회 |
| V3__create_p_order_creations.sql | 시도·주문자·배송 ID, orderId UNIQUE, 상태 CHECK와 복구 조회 인덱스 |
| OrderCreationAttempt | 요청 안에서 유지하는 시도·주문·주문자 식별자 |
| OrderCreationCommitResult | COMMITTED·NOT_COMMITTED·UNKNOWN과 확인된 주문 결과 |
| 기존 OrderWriter | 시작·주문/완료 저장·재확인·종료·복구 기록의 독립 트랜잭션 |
| 기존 OrderService | 원격 호출·결과 재확인·보상 판단과 최초 오류 유지 |

## 왜 이렇게 만들었는가

**주문 행만으로는 실패를 추적할 수 없다.** 주문 저장 전에 실패하면 p_orders에는 아무것도 없다. 그래서 원격 변경 전에 p_order_creations를 먼저 커밋한다.
이 기록은 주문보다 먼저 존재하므로 p_orders로 FK를 만들지 않는다. orderId는 UNIQUE이고 사용자·배송 ID도 논리 참조로 보관한다.

**주문과 완료 기록을 한 번에 확정한다.** 주문은 저장됐는데 기록이 PROCESSING으로 남거나, 기록만 COMPLETED가 되는 정상 경로를 만들지 않는다.
두 변경과 이벤트 발행을 같은 로컬 트랜잭션에 넣는다. 완료 기록의 CHECK가 실패하는 경우에도 주문 INSERT와 함께 롤백됨을 검증했다.

**커밋 예외를 롤백이라고 단정하지 않는다.** DB가 커밋한 뒤 응답만 끊기면 호출자는 예외를 받더라도 데이터는 남는다. 바로 I-13을 호출하면 정상 주문의 배송을 삭제할 수 있다.
새 트랜잭션의 resolveCreation이 실제 데이터를 확인한다. 확인할 수 없으면 삭제를 추측해서 실행하지 않고 복구 대상으로 남긴다.

**진행 기록을 먼저 잠가야 한다.** 원래 INSERT가 아직 커밋되지 않았다면 다른 트랜잭션에서 주문이 보이지 않을 수 있다. 단순 findById의 빈 결과만으로 미저장을 판정하면 너무 이르다.
save와 resolveCreation이 미리 커밋된 같은 기록을 잠그므로, 재확인은 원래 저장 트랜잭션이 끝날 때까지 기다린 뒤 판단한다.

**확인된 주문 결과를 재사용한다.** 커밋이 확인되면 새로운 저장이나 이벤트 발행 없이 DB의 OrderResult를 반환한다. 이미 제출한 생성 알림을 다시 보내지 않는다.
실제 커밋 응답 유실로 AFTER_COMMIT 자체가 실행되지 않는 경우의 알림 유실까지 보장하지는 않는다. Outbox는 별도 검토 대상이다.

**삭제 성공과 지연 생성 종료는 다르다.** I-09 처리 중 타임아웃이 나고 I-13이 먼저 204를 반환한 뒤, 원래 I-09가 늦게 완료될 수 있다.
기존 SA의 삭제 보상은 유지하지만 기록은 RECOVERY_REQUIRED로 남긴다. 자동으로 정리 완료했다고 선언하지 않으며 제공 서비스의 추가 보장은 DELIVERY-002에서 확인한다.

**생성 기록은 외부 멱등키가 아니다.** 현재 외부 POST를 다시 보내면 새 orderId를 발급한다. 전체 원본 요청이나 가격 스냅샷을 재실행용으로 저장하지도 않는다.
이번 범위는 결과 확인과 실패 추적이다. Idempotency-Key·요청 해시·원본/결과 보존·진행 조회는 후속 계약으로 정한다.

## 검증

- 도메인 테스트를 먼저 작성해 타입 미구현 실패를 확인했다.
- 신규 40개: 도메인 11개·application 11개·PostgreSQL 15개·기존 HTTP 테스트에 3개 추가. 기존 생성 테스트의 진행 기록 검증도 보강했다.
- `./gradlew build --console=plain` 성공. 주문 전체 292개·전체 XML 390개, 실패·오류·skip 0. 다른 모듈 테스트는 기존 성공 결과를 재사용했다.
- 실제 DB 커밋이 끝난 뒤 transaction advice 바깥에서 예외를 주입했다. 주문 반환·배송 보존·알림 1회와, 재확인 실패가 겹쳐도 완료 기록을 덮지 않음을 확인했다.
- PostgreSQL의 pg_blocking_pids로 재확인 쿼리의 잠금 대기를 관찰한 뒤 원래 트랜잭션을 커밋·롤백했다. 결과가 각각 COMMITTED·NOT_COMMITTED로 구분됐다.
- 완료 기록 저장 실패·이벤트 발행 실패에서 주문/기록 롤백, 미저장 뒤 삭제 실패의 사유 보존, I-13 선행 후 늦은 I-09 완료에도 복구 대기 유지를 확인했다.
- 별도 Gateway·JWT·실제 Feign·PostgreSQL 16에서 201·400·401·500·503을 확인했다. 시작 기록 실패 시 원격 호출 없음, 진행 기록 Audit, 위조 사용자 헤더 제거도 검증했다.
- 제공 user·company·delivery 응답은 HTTP 대역이다. 실제 커밋 응답 패킷 유실·프로세스 강제 종료·팀 서비스 업무 구현 통합은 미확인이다.
- 격리 실행 근거는 임시 `logistics-order-creation-progress-smoke-db3ol3i1/result.json`과 서버 로그다. 임시 프로세스·DB를 정리했고 실제 Slack 메시지는 보내지 않았다.
- 테스트의 Order 타입 이름 충돌은 TS-013에 기록했다. 도메인 이름은 유지하고 Spring 애너테이션만 전체 이름으로 표시했다.
- 로컬은 `docker compose build order-service` 후 `up -d --no-deps --no-build --wait --wait-timeout 90 order-service`로 주문만 교체했다. 새 이미지 일치·Flyway V3·생성 기록 FK 없음·health UP을 확인했다.
- Gateway O-01 OpenAPI/201 응답 계약·미인증 POST 401을 확인했다. 기존 주문·취소 기록 해시, 나머지 8개 컨테이너 ID·시작 시각, PostgreSQL 볼륨, .env 해시·Webhook 값이 유지됐다. 실제 개발 DB에는 생성 기록이 0건이며 업무 주문을 새로 만들지 않았다.
- 문서 동기화·에러 코드 16개·공백·변경 범위 32개 파일의 로컬 링크·협업 앵커 검사를 통과했다. 실제 Webhook 값이 변경 파일·최근 주문 로그에 없고 다른 작업의 Q&A 변경 3개는 보존했다.

## 다른 서비스 담당자가 읽을 기록

- [중앙 서비스 창구](../../../services/README.md)에서 [연동 실패 계약](../../../services/order/integration-contract.md)과 [상세 예외 흐름](../../../services/order/failure-handling.md)으로 연결한다.
- company README의 COMPANY-001에 I-07·I-08 응답·가격·오류 전파·실제 연동 확인을 요청했다.
- delivery README의 DELIVERY-001에 기존 생성·취소·원복·삭제 계약의 구현 검증을, DELIVERY-002에 지연 생성·삭제의 순서 역전과 종료 표시·시도 토큰·결과 확인 수단 검토를 남겼다.
- 모두 받는 담당자의 확인을 기다리는 OPEN이다. 다른 서비스 소스 코드는 고치지 않았고, 작성자 판단으로 팀 합의·검증 완료로 표시하지 않았다.

## 직접 해보기

1. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderCreation*' --tests '*OrderHttpIntegrationTest'`를 실행한다. 생성 관련 59개 테스트를 재현한다.
2. IntelliJ에서는 OrderCreationTest·OrderCreationServiceTest·OrderCreationRecordIntegrationTest·OrderCreationIntegrationTest·OrderHttpIntegrationTest 클래스 옆 실행 버튼을 사용한다.
3. OrderWriter.save와 resolveCreation이 같은 findByOrderIdForUpdate를 먼저 호출하는지 읽는다. 잠금 대상이 p_orders가 아니라 생성 기록인 이유를 설명해 본다.
4. 실제 데이터 변경 없이 실패 상태를 확인하려면 [예외 처리 설계](../../../services/order/failure-handling.md) 6절의 생성 기록 SELECT를 읽는다. 종료 기록도 특정 orderId로 확인해야 하는 경우를 구분한다.
5. 외부 연동 준비 시 [연동 계약](../../../services/order/integration-contract.md)의 시나리오를 제공 서비스 담당자와 확인하고 각 협업 로그에 결과를 남긴다.

## 참고

- [ADR 0007](../../../services/order/decisions/0007-order-creation-progress.md), [SA 5.5·7.1](../../../SA/sparta-logistics-sa.md)
- [진행 상황](../../../progress/feat-order-create.md), [트러블슈팅 TS-013](../../../troubleshooting.md)
