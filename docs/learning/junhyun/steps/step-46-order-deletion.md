# 46. 주문 삭제와 기본 API 마무리

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `ed9f8d0 feat(order): 주문 생성 멱등키와 최초 응답 재사용 구현`
- 이번 커밋 제목: `feat(order): 주문 삭제와 재요청 복구 구현`
- 범위: O-06·I-10·V5 삭제 진행 기록·검증·문서. 사용자 승인으로 단계마다 멈추지 않고 마무리했다.

## 바뀐 동작

`DELETE /api/v1/orders/{orderId}`를 구현해 기본 주문 API O-01~O-06이 모두 연결됐다. MASTER만 호출하며 본문 없이 보내고 성공은 200·data=null이다.

| 조건 | 결과 |
|---|---|
| USER | 소유자여도 403 FORBIDDEN |
| 없음·이미 논리 삭제 | 404 ORDER_NOT_FOUND |
| 주문 CANCELED | 배송 재조회 없이 삭제 진행 |
| 주문 ORDERED | I-10의 주문·배송 ID와 DELIVERED 확인 후 진행 |
| 진행 중인 배송·미해결 취소 | 409 ORDER_NOT_DELETABLE |
| 기존 삭제 기록이 PROCESSING·RECOVERY_REQUIRED | 저장된 허가 근거로 같은 삭제를 계속함 |
| 삭제 진행 기록 자체가 삭제됨·참조 불일치 | 409, 수동 확인 |

삭제는 주문·배송·배송 구간의 논리 삭제다. 생성의 멱등키·최초 응답과 취소 이력은 유지하며 Slack은 보내지 않는다.
삭제 후 O-03은 404지만 같은 생성 멱등키의 O-01은 최초 생성 결과를 반환한다. 재응답의 생성 영수증과 현재 주문 상태를 구분한다.

## 순서와 계층별 역할

1. OrderDeletionController가 MASTER 역할·UUID를 확인하고 OrderDeletionService로 위임한다. 삭제 입력 DTO나 출력 Entity를 추가하지 않았다.
2. Service가 권한을 다시 확인하고 Writer.inspect로 현재 주문과 삭제 기록을 읽는다. 새 ORDERED 주문만 트랜잭션 밖에서 I-10을 조회한다.
3. Writer.start가 주문을 잠가 조건·미해결 취소를 재검증하고 삭제 허가 근거를 별도 커밋한다.
4. Writer.delete가 같은 주문 잠금 아래 주문 논리 삭제·flush → I-13 → 진행 기록 COMPLETED를 실행하고 커밋한다.
5. 예외가 나면 새 트랜잭션의 Writer.resolve로 실제 삭제 커밋을 확인한다. 완료면 성공, 나머지는 기록을 보존하고 최초 오류를 반환한다.
6. MASTER의 동일 DELETE 재요청은 기존 기록을 사용한다. 이전 I-13이 성공해 배송이 사라졌더라도 I-10을 반복하지 않는다.

| 위치 | 담당하는 것 |
|---|---|
| Order.validateDeletable | 취소 또는 배송 완료라는 삭제 조건 |
| OrderDeletion·상태·근거·사유 enum | 삭제 전에 확인한 조건과 진행 기록 |
| V5·OrderDeletionRepository | 같은 주문당 한 기록, 같은 스키마 FK·CHECK·조회 인덱스 |
| DeliveryReader·DeliveryQueryClient·ReaderAdapter | 기존 I-10 호출, 두 ID·알려진 배송 상태 검증 |
| OrderDeletionWriter | 사전 기록·실제 삭제·커밋 재확인·실패 기록의 트랜잭션 |
| OrderDeletionService | 권한·호출 순서·예외 후 실제 결과 판단 |
| OrderCancellationWriter | 삭제가 시작된 주문의 새 취소 차단 |
| OrderDeletionController | O-06·MASTER 권한·200 공통 응답 |

## 왜 이렇게 만들었는가

**배송 삭제가 성공해도 주문 DB는 실패할 수 있다.** 로컬 트랜잭션 롤백으로 다른 서비스의 삭제를 되돌릴 수 없다. 원래 SA의 “I-13을 다시 호출하면 수렴”에는 삭제 조건을 어떻게 다시 확인할지 빠져 있었다.
특히 ORDERED·DELIVERED 주문은 배송 삭제 후 I-10이 404를 반환한다. 삭제 전의 완료 상태를 잃지 않도록 `DELIVERY_DELIVERED`를 먼저 기록한다. 취소 주문은 `ORDER_CANCELED`를 기록한다.

**404를 삭제 허가로 사용하지 않는다.** 처음부터 잘못된 배송 ID였던 주문과, 이번 삭제가 이미 배송에 반영된 주문은 다르다.
기록이 없는 최초 요청은 정확한 ID·완료 상태를 요구한다. 기록이 있는 재요청만 이전 확인 결과를 사용한다.

**삭제 기록은 주문과 다른 시점에 커밋한다.** 주문 삭제와 허가 기록을 같은 트랜잭션에 넣으면 롤백 때 허가 기록도 사라진다.
먼저 PROCESSING을 커밋하고 실제 삭제에서는 주문 논리 삭제와 COMPLETED를 함께 확정한다. 다른 DB 연결에서도 시작 기록만 먼저 보이는지 테스트했다.

**같은 주문 잠금으로 취소와 삭제를 조정한다.** 조회 후 잠금 없이 조건을 믿으면 그 사이 새 취소가 시작될 수 있다.
삭제 시작·실행과 취소 시작이 같은 주문을 잠그며, 미해결 취소가 있으면 삭제를, 삭제 기록이 있으면 새 취소를 막는다. 모든 행을 잠그는 전역 잠금은 만들지 않았다.

**O-06은 SA 7.5의 트랜잭션 경계를 유지한다.** I-13 실패 시 로컬 삭제를 롤백하기 위해 I-13 동안 주문 잠금·DB 연결을 유지한다.
이는 비용이 있는 선택이다. I-10은 밖에서 조회하고 I-13은 요청당 1회로 한정한다. 생성 실패 보상의 최대 3회 호출 정책은 유지하며 그 루프를 삭제에 중첩하지 않는다.

**삭제 재개와 새 생성·취소는 다르다.** 삭제는 이미 확인한 종료 동작을 같은 orderId로 반복해 마무리한다. I-13의 “없음·이미 삭제도 204” 계약을 사용한다.
생성·취소의 결과 불확실한 새 시도를 다시 시작하는 정책은 바꾸지 않았다. 자동 복구 스케줄러나 배송 복원 API도 추가하지 않았다.

## 검증 결과

- 도메인 테스트를 먼저 작성해 OrderDeletion 미구현 컴파일 실패를 확인한 뒤 구현했다.
- 신규 74개: 도메인 7개·application 10개·MVC 8개·PostgreSQL 21개·HTTP/Feign 28개.
- `./gradlew build --console=plain` 성공. 주문 421개·전체 XML 519개, 실패·오류·skip 0. 다른 모듈은 기존 성공 결과를 재사용했다.
- 실제 PostgreSQL에서 CHECK·지연 제약 트리거로 flush·완료 기록·커밋 실패를 주입했다. 배송 호출 이후 롤백도 같은 삭제 재요청으로 완료됐다.
- 실제 커밋 뒤 transaction advice 바깥의 반환 예외와 재확인 실패를 주입했다. 정상 삭제·COMPLETED는 보존되고 배송을 다시 호출하지 않았다. 실제 네트워크 커밋 패킷 유실 시험은 아니다.
- 동일 주문의 두 삭제를 보내 PostgreSQL의 pg_blocking_pids로 잠금 대기를 확인했다. I-13 호출은 1회였고 뒤 요청은 404였다. 미해결 취소·삭제 기록의 상호 차단도 확인했다.
- I-10 응답 ID 불일치·필드 누락·모르는 상태를 503으로 거부했다. I-10 통신 실패 3회·5xx 1회, I-13 통신 실패·5xx 1회 호출을 검증했다.
- I-13은 정확한 204를 확인한다. 200·202를 완료로 처리하던 기존 void Feign 반환을 실패 테스트로 재현하고 ResponseEntity<Void> 검사로 보강했다(TS-017). 생성 보상의 기존 3회 정책도 전체 회귀 테스트에서 유지됐다.
- 실제 Gateway·JWT·Feign·격리 PostgreSQL에서 200·201·400·401·403·404·409·500·503을 확인했다. 생성→취소→삭제, 배송 완료 삭제, 위조 MASTER 헤더 거부, 삭제 후 생성 멱등키 재응답을 검증했다.
- 배송만 삭제한 뒤 로컬 완료 기록을 실패시켜 500을 확인하고, 같은 DELETE가 I-10 없이 200으로 마무리됨을 검증했다. 실행 근거는 임시 `logistics-order-delete-smoke.mny4g6/result.json`과 서버 로그다.
- 최초 스모크의 장애 주입 DDL 위치 때문에 생긴 순환 대기는 TS-015에 기록했다. 주입을 요청 전에 수행하도록 수정했고 재실행은 통과했다. 임시 JVM·DB는 정리했다.
- 로컬은 `docker compose build order-service` 후 `up -d --no-deps --no-build --wait --wait-timeout 90 order-service`로 주문만 교체했다. V5·주문 ID UNIQUE·같은 스키마 FK·health UP을 확인했다.
- 기존 주문·생성·취소 데이터(각 0건), 다른 8개 컨테이너 ID·시작 시각, PostgreSQL 볼륨·.env·Webhook 값이 유지됐다. 새 삭제 기록도 0건이며 실제 업무 데이터는 만들지 않았다.
- 로컬 Gateway OpenAPI의 O-06·200과 기존 O-01 필수 멱등키, 미인증 DELETE의 401을 확인했다.
- 최종 소스로 Gateway 검증과 주문 이미지 반영을 다시 확인했다. 내부 202 응답 거부도 포함한다. 문서 동기화·에러 코드 21개·공백·42개 작업 파일의 로컬 링크와 신규 파일 검사를 통과했다. SA 주문 ERD 렌더·DBML 파싱·삭제 FK 방향도 확인했다.
- 외부 user·company·delivery는 HTTP 대역이다. 팀의 실제 업무 구현 연동·실제 커밋 응답 패킷 유실·다중 JVM·강제 종료 검증은 남아 있다. 실제 Slack 메시지는 추가로 보내지 않았다.

## 직접 해보기

1. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderDeletion*'`로 삭제 관련 74개를 실행한다. IntelliJ에서는 위 이름의 테스트 클래스 실행 버튼을 사용한다.
2. OrderDeletionIntegrationTest의 `resumesAfterRemoteDeleteAndLocalRollback`을 읽는다. 최초 요청에서 원격 삭제 후 로컬 DB가 롤백되는 지점을 찾는다.
3. OrderDeletionHttpIntegrationTest의 `resumesUncertainDelete`를 실행한다. 두 번째 요청에서 배송 GET이 0회인 이유를 설명한다.
4. OrderDeletionWriter의 start와 delete가 별도 트랜잭션인지, 삭제 후 원래 O-01 스냅샷을 지우는 코드가 없는지 확인한다.
5. `docker compose exec -T order-service curl -fsS http://localhost:8084/actuator/health`에서 UP을 확인한다. 실제 Slack Webhook 값은 출력하지 않는다.

## 기본 완료와 남은 범위

기본 명세의 주문 생성·검색·단건 조회·요청사항 수정·취소·삭제와 생성 후 Slack 코드를 구현했다. 기본 API를 더 추가할 필요는 없다.
팀 company·delivery의 실제 데이터로 정상·실패 흐름을 통합 검증하는 일은 남아 있다. 기존 COMPANY-001·DELIVERY-001·DELIVERY-002는 제공 담당자의 확인을 기다리는 OPEN이다.
생성 진행 조회 API, 생성·취소 운영 복구, 시간 예산·관측 보강, Outbox는 선택 후속 작업이다. 이번 완료 범위에 섞지 않는다.

## 확인 질문

- 배송만 삭제된 뒤 주문 DB가 롤백되면, 삭제 전에 확인한 근거가 없는 구현은 어디서 막히는가?
- I-13 동안 로컬 트랜잭션을 유지해도 두 서비스가 하나의 원자적 트랜잭션이 되는 것은 왜 아닌가?
- 같은 삭제의 재개와 미확정 생성의 새 실행은 어떤 점이 다른가?

기준: [ADR 0009](../../../services/order/decisions/0009-order-deletion-progress.md), [연동 계약](../../../services/order/integration-contract.md), [실패 처리 설계](../../../services/order/failure-handling.md), [진행 기록](../../../progress/feat-order-create.md).
