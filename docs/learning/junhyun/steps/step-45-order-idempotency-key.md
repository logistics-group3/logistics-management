# 45. 주문 생성 Idempotency-Key와 최초 응답 재사용

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `39f704a feat(order): 생성 진행 기록과 커밋 결과 재확인 구현`
- 이번 변경: O-01 필수 멱등키·V4·요청 지문·최초 응답·재요청 및 동시성 검증, 미커밋

## 바뀐 동작

주문 생성에 `Idempotency-Key` 헤더를 반드시 보낸다. 새 주문을 제출할 때 키를 한 번 만들고, 응답을 못 받아 재시도할 때는 **그 키와 본문을 그대로** 보낸다.
UUID를 권장하며 1~128자 영문·숫자·`.`·`_`·`:`·`-`를 허용한다. 인증 사용자 ID별로 구분하고 키의 대소문자를 구별한다.

```http
POST /api/v1/orders
Authorization: Bearer {accessToken}
Idempotency-Key: 8f2d6f94-a287-4b6d-82c9-4828a2a72ef3
Content-Type: application/json
```

| 기존 기록과 요청 | 결과 |
|---|---|
| 키 없음, 유효한 새 요청 | 생성 진행 기록을 먼저 커밋한 뒤 기존 배송·주문 생성 진행 |
| 같은 키·본문, COMPLETED | 최초 주문 생성 응답으로 201, 새 주문·배송·알림 없음 |
| 같은 키, 다른 유효한 본문 | 409 ORDER_IDEMPOTENCY_KEY_CONFLICT |
| 같은 키·본문, PROCESSING | 409 ORDER_CREATION_IN_PROGRESS |
| 같은 키·본문, ABORTED | 409 ORDER_CREATION_ABORTED, 같은 키로 재실행하지 않음 |
| 같은 키·본문, RECOVERY_REQUIRED 또는 생성 기록 삭제 | 409 ORDER_CREATION_RECOVERY_REQUIRED |

헤더·본문의 형식 검증은 재요청에도 적용한다. 최초 업체·상품 조회나 권한 검증에서 거절되면 아직 키를 저장하지 않는다.
PROCESSING·RECOVERY_REQUIRED를 새 키로 우회하면 별개 주문을 만들 수 있으므로 기존 결과부터 확인한다. ABORTED의 원인을 해결한 별도 새 주문에는 새 키를 쓴다.

## 처리 순서와 파일 역할

1. Controller가 필수 헤더·본문을 검증하고 CreateOrderCommand를 만든다.
2. Command가 파싱한 입력의 지문을 만들고 OrderWriter.findReplay가 사용자·키를 조회한다.
3. 기존 키면 도메인에서 본문·상태를 판정한다. 완료는 OrderResult로 변환하고 나머지는 전용 409다.
4. 새 키면 업체·상품·권한을 확인하고 OrderWriter.start의 별도 트랜잭션으로 PROCESSING을 커밋한다.
5. 원격 배송 생성은 트랜잭션 밖에서 수행한다. OrderWriter.save가 주문·COMPLETED·최초 응답 스냅샷을 한 번에 커밋한다.
6. 동시 INSERT가 유니크 제약에 걸리면 해당 트랜잭션을 종료하고 새 트랜잭션에서 기존 키를 읽는다. 원격 생성으로 넘어가지 않는다.

| 파일 | 담당하는 것 |
|---|---|
| CreateOrderCommand.identity | 파싱한 UUID·수량·요청사항의 v1 SHA-256 지문 |
| OrderRequestIdentity | 키·지문의 형식 불변식 |
| OrderCreation.replay | 본문 충돌·진행 상태별 재응답 규칙 |
| OrderCreationSnapshot | 최초 생성 결과를 보관하는 불변 record |
| OrderWriter.findReplay·start·save | 짧은 조회·키 선점·주문 완료 트랜잭션 |
| OrderCreationJpaRepository | 사용자·키로 삭제 이력까지 조회 |
| V4__add_order_idempotency.sql | 키·지문·JSONB, 사용자와 키의 UNIQUE·CHECK |

## 왜 이렇게 만들었는가

**서버가 만든 orderId만으로 외부 재요청을 알아볼 수 없다.** 이전에는 POST가 들어올 때마다 새 orderId를 만들었다. 처음 응답을 못 받은 클라이언트는 그 ID를 알 수 없다.
클라이언트가 처음부터 알고 있는 키를 받으면 같은 주문 제출을 식별할 수 있다. 내부 I-09는 이와 별개로 기존 orderId 멱등 계약을 유지한다.

**조회만으로 동시 요청을 막을 수 없다.** A와 B가 동시에 조회하면 둘 다 “키 없음”을 볼 수 있다. 메모리 잠금은 서버가 여러 대일 때 보호 범위가 달라진다.
DB의 `(orderer_id, idempotency_key)` UNIQUE가 마지막에 한 요청만 통과시킨다. 선점에 성공한 요청만 배송을 만들며 다른 요청은 201 또는 상태별 409로 끝난다.

**유니크 충돌 뒤에는 새 트랜잭션에서 읽는다.** PostgreSQL에서 SQL 오류가 난 트랜잭션으로 계속 조회하면 그 조회도 실패할 수 있다.
OrderWriter.start는 별도 빈의 REQUIRES_NEW 메서드다. 프록시가 롤백을 마친 뒤 OrderService가 예외를 잡고, 별도 findReplay 트랜잭션을 연다.

**키가 같아도 본문이 다르면 같은 요청이 아니다.** 키만 비교하면 수량 50의 응답을 수량 100의 요청에 돌려줄 수 있다. 입력 지문을 함께 저장해 이를 409로 거부한다.
원본 JSON 문자열 대신 파싱한 UUID·수량·요청사항을 지문화한다. JSON 속성 순서·공백·UUID 대문자 차이는 무시하고 null과 빈 요청사항은 구별한다.
문자열 길이와 null 표식을 포함해 경계를 명확하게 한다. 잘못된 유니코드를 대체 문자로 바꾸면 다른 입력이 같은 지문이 될 수 있어 엄격한 UTF-8 인코딩으로 거부한다.

**현재 주문과 최초 생성 결과는 역할이 다르다.** 생성 후 요청사항을 고치거나 취소하면 Order 값이 달라진다. 재요청에 현재 Order를 반환하면 최초 응답을 재현할 수 없다.
불변 응답 스냅샷을 주문·COMPLETED와 같은 커밋에 저장한다. 재요청은 이 스냅샷을 반환하고 현재 상태는 O-03에서 조회한다. JSONB 매핑은 기존 Hibernate 기능을 사용했다.

**저장된 키를 지우거나 만료시키면 오래된 재요청이 다시 실행된다.** 그래서 TTL을 넣지 않고 논리 삭제 이력도 UNIQUE와 조회에 포함했다.
일반 주문 조회의 삭제 제외 규칙은 유지한다. 키 조회만 의도적으로 삭제 이력을 읽고 복구 필요 409를 반환한다. 보존 기간 변경은 별도 계약이 필요하다.

**멱등성은 재실행을 제한하는 기능이다.** 실패한 주문을 자동으로 성공시키거나 원격 배송의 모든 지연을 없애지는 않는다.
I-09 타임아웃 뒤 I-13보다 늦게 배송이 만들어지는 문제는 여전히 DELIVERY-002의 확인 대상이다. Slack은 기존 메모리 이벤트 방식이며 재응답에서 재발행하지 않지만 전송 자체의 유실까지 보장하지는 않는다.

## 검증

- 지문·도메인 테스트를 먼저 작성해 미구현 실패를 확인했다. 잘못된 유니코드 입력의 지문 충돌 가능성도 실패 테스트로 재현한 뒤 수정했다.
- 신규 55개: 지문 12개·도메인 11개·PostgreSQL 16개·MVC 7개·HTTP 7개·application 경쟁 처리 2개.
- `./gradlew build --console=plain` 성공. 주문 347개·전체 테스트 XML 445개, 실패·오류·skip 0. 다른 모듈 테스트는 기존 성공 결과를 재사용했다.
- 실제 PostgreSQL에서 두 요청을 동기화해 둘 다 키 없음 조회를 통과시켰다. 같은 본문·다른 본문 경쟁 모두 한 주문·배송·이벤트만 생성하고, 유니크 충돌 뒤 새 트랜잭션의 조회가 성공했다.
- JSONB 왕복, 수정·취소·논리 삭제 이후 원래 응답, 다른 사용자·대소문자 키 구분, 삭제된 키 재사용 차단, DB CHECK를 검증했다.
- 격리 스키마에서 실제 V3 SQL로 기존 완료 행을 만든 뒤 V4를 적용했다. 기존 행의 데이터와 NULL 멱등 필드를 유지했다.
- 실제 HTTP에서 헤더 누락·형식·길이·중복 값, OpenAPI 필수 헤더, JSON 순서·UUID 표현 차이, 본문 충돌, 처리 중·실패·복구 대기 409를 확인했다.
- 별도 Gateway·JWT·실제 Feign·PostgreSQL 16에서 201·400·401·409·500·503을 확인했다. 헤더 전달·위조 사용자 헤더 제거·원래 응답 재사용·배송 1회도 검증했다.
- 격리 실행 근거는 임시 `logistics-order-idempotency-smoke.5Jy2OH/result.json`과 서버 로그다. user·company·delivery는 HTTP 대역이며 실제 Slack 메시지를 보내지 않았다. 임시 JVM·DB는 정리했다.
- 타입 추론과 오버로드 때문에 발생한 테스트 컴파일 오류는 TS-014로 기록했다.
- `docker compose build order-service` 후 `docker compose up -d --no-deps --no-build --wait --wait-timeout 90 order-service`로 주문만 반영했다. 준비한 이미지 일치·Flyway V4·사용자/키 UNIQUE·JSONB·health UP을 확인했다.
- 실제 로컬 Gateway의 OpenAPI에서 필수 헤더·길이·패턴과 201 계약, 미인증 POST의 401을 확인했다. 기존 주문·생성·취소 데이터(각 0건), 다른 8개 컨테이너 ID·시작 시각, PostgreSQL 볼륨·.env·Webhook 값이 유지됐다.
- 문서 동기화·에러 코드 20개·공백·작업 범위 36개 파일의 로컬 링크 141개를 검사했다. SA 주문 ERD·생성 시퀀스를 렌더해 확인하고 DBML 파싱도 통과했다. 실제 Webhook 값은 변경 파일·최근 주문 로그에 없으며 별도 학습 파일 4개와 progress의 GitHub 기록은 보존했다.

## 직접 해보기

1. Docker가 실행 중일 때 `./gradlew :order-service:test --tests '*OrderRequestIdentityTest' --tests '*OrderIdempotency*'`를 실행한다. 지문·도메인·PostgreSQL 39개를 확인한다.
2. IntelliJ에서는 OrderIdempotencyIntegrationTest의 동시 요청 테스트를 실행한다. CyclicBarrier가 두 요청을 어디서 기다리게 하는지, 배송 호출이 왜 하나인지 읽는다.
3. `./gradlew :order-service:test --tests '*OrderHttpIntegrationTest' --tests '*OrderControllerWebMvcTest'`로 HTTP·MVC 41개를 확인한다. 결과는 `order-service/build/reports/tests/test/index.html`에서 본다.
4. OrderService의 DataIntegrityViolationException 처리와 OrderWriter의 REQUIRES_NEW를 함께 읽는다. 한 메서드의 try/catch 안에서 INSERT·SELECT를 계속하는 경우와 비교한다.
5. 테스트에서 완료된 Order를 취소한 뒤 같은 키·본문으로 다시 요청해 본다. 응답은 최초 ORDERED이고 현재 DB는 CANCELED인 이유를 설명한다.

## 확인 질문과 남은 범위

- HTTP 재시도 때마다 새 UUID를 만들면 이 기능이 중복을 막을 수 있는가?
- 같은 키의 실패 종료와 복구 대기를 왜 다른 코드로 돌려주는가?
- 유니크 제약 없이 조회 결과만 믿으면 어떤 실행 순서에서 두 주문이 생기는가?

다음 후보는 생성 진행 조회 API다. 미확정 결과를 해소하는 복구 유스케이스, 실제 팀 서비스 통합, 다중 JVM·프로세스 강제 종료 시험은 남아 있다.
현재 기능은 같은 사용자·키 범위의 중복 실행을 막는다. 다른 키로 제출한 동일 품목·수량을 업무상 중복으로 거절하는 규칙은 아니다.

기준은 [ADR 0008](../../../services/order/decisions/0008-order-idempotency-key.md), 호출자 안내는 [연동 계약](../../../services/order/integration-contract.md), 실패 후 판단은 [예외 처리 설계](../../../services/order/failure-handling.md)에 있다.
