# 42. 주문 요청사항 수정 API

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `9a41925 feat(order): 주문 검색과 단건 조회 구현`
- 이번 변경 커밋: `6ac9b8a feat(order): 주문 요청사항 수정 API 구현`

## API 계약

```http
PATCH /api/v1/orders/{orderId}
Authorization: Bearer {accessToken}
Content-Type: application/json

{"requestNote":"오후 도착 희망"}
```

주문자 본인과 MASTER만 사용할 수 있고 성공하면 수정된 OrderResponse를 담은 ApiResponse를 200으로 반환한다.
상품·수량 변경은 금액·배송을 다시 만들어야 하므로 SA에서 지원하지 않는다. 요청 DTO에 requestNote만 두어 다른 필드가 들어와도 주문 스냅샷을 바꾸지 않는다.

| 조건 | 응답 |
|---|---|
| requestNote 누락·null·빈 문자열·공백·500자 초과 | 400 VALIDATION_ERROR |
| 인증 정보 없음 | 401 UNAUTHORIZED |
| 다른 USER의 주문 | 403 FORBIDDEN |
| 없는 주문·논리 삭제된 주문 | 404 ORDER_NOT_FOUND |
| 취소된 주문, MASTER 포함 | 409 INVALID_STATUS_TRANSITION |

SA 6.7의 O-04는 requestNote가 필수이고, SA 8.5는 필수 문자열의 공백을 금지한다. 따라서 빈 문자열로 지우는 기능은 현재 계약에 없다.
O-01 생성에서는 선택 항목이므로 null을 허용한다. 생성과 수정의 입력 규칙을 억지로 하나로 합치지 않는다.

## 만든 것과 역할

| 파일·타입 | 역할 |
|---|---|
| `domain/Order.updateRequestNote` | 취소 상태·공백·최대 길이를 검사하고 요청사항만 변경 |
| `domain/OrderRepository` | 쓰기용 findByIdForUpdate·flush 계약 |
| `infrastructure/persistence/OrderJpaRepository` | PESSIMISTIC_WRITE 잠금과 JPQL 조회 연결 |
| `application/OrderService.updateRequestNote` | 쓰기 트랜잭션·소유자 확인·변경·Audit 결과 반환 |
| `application/dto/UpdateOrderCommand` | HTTP와 분리한 수정 입력 |
| `presentation/dto/UpdateOrderRequest` | @NotBlank·@Size(max=500), Command 변환 |
| 기존 `OrderController`, `OrderResult`, `OrderResponse` | PATCH 연결과 기존 응답 계약 재사용 |

request_note 컬럼이 이미 있으므로 새 마이그레이션은 필요하지 않다. 금액은 합의된 long·BIGINT를 유지한다.

## 왜 이렇게 만들었는가

**Entity는 유효한 상태만 만들게 한다.** `updateRequestNote`는 취소된 주문을 거부하고 입력값을 검사한다. 다른 서비스 메서드가 호출해도 같은 규칙을 지킨다.
HTTP 입구의 Bean Validation은 잘못된 요청을 일찍 걸러 필드 오류를 반환한다. Entity 검증은 HTTP를 거치지 않는 호출에서도 주문 규칙을 보호한다.

**소유자 확인은 변경 전에 한다.** 서비스는 잠금으로 주문을 조회하고 `validateSelfOrMaster(ordererId)`를 호출한다. 타인 주문이면 상태 검사나 수정 전에 403을 반환한다.
상품·업체의 현재 담당자를 다시 조회하지 않는다. 이 API의 권한 기준은 주문에 저장된 주문자다.

**같은 주문의 상태 확인과 수정을 한 트랜잭션으로 묶는다.** 비관적 쓰기 잠금은 다른 트랜잭션이 같은 행의 쓰기 잠금을 얻거나 수정하려 할 때 기다리게 한다.
먼저 진행 중인 취소가 커밋되면 대기하던 수정은 최신 CANCELED 상태를 보고 거부한다. 먼저 삭제가 커밋되면 SQLRestriction의 삭제 제외 조건으로 404가 된다.
단순 조회 API에는 잠금을 추가하지 않았다. 잠금 동안 원격 호출을 하지 않으며 후속 취소·삭제의 로컬 상태 변경도 같은 잠금 계약을 따라야 한다.

**기존 Entity는 변경 감지로 저장한다.** 트랜잭션에서 조회한 Order는 JPA가 관리한다. 필드가 달라지면 flush 시 변경 감지로 UPDATE를 실행하므로 save를 다시 호출할 필요가 없다.
같은 요청사항을 다시 보내면 변경이 없어 수정 시각도 유지된다. 무조건 새 UPDATE를 만드는 로직을 추가하지 않았다.

**flush와 커밋은 다르다.** Audit의 updatedAt·updatedBy는 변경 내용을 DB에 반영하는 과정에서 갱신된다. 응답 DTO를 먼저 만들면 이전 수정 시각을 복사할 수 있어 flush 후 결과를 만든다.
flush는 SQL을 실행하는 단계다. 트랜잭션이 롤백되면 요청사항과 Audit 모두 원복되며 서비스 트랜잭션 프록시가 정상 커밋된 후에 Controller로 결과를 전달한다.

**생성용 저장 흐름은 재사용하지 않는다.** OrderWriter는 생성 이벤트를 발행한다. 수정에 재사용하면 생성 Slack 알림이 다시 전송될 수 있다.
O-04는 주문 DB만 수정한다. 회사·배송 호출, 새 배송 생성, 생성 이벤트·Slack 전송을 하지 않는다.

## 검증

- 도메인 테스트를 먼저 작성해 메서드 미구현 컴파일 실패를 확인한 뒤 구현했다.
- 신규 테스트 39개: 도메인 7개, application 7개, MVC 11개, 실제 HTTP/PostgreSQL 14개.
- `./gradlew build --console=plain` 성공. 주문 테스트 191개를 실행했고 전체 XML 합계 289개, 실패·오류·skip 0이다. 다른 모듈은 기존 성공 결과를 재사용했다.
- 주문자·MASTER 성공, 타인 거부, 취소 거부, 없음·삭제 404, 입력 검증, 500자 경계, 다른 필드 보존, Audit 일치, 같은 값 재요청을 확인했다.
- PostgreSQL의 pg_blocking_pids로 실제 대기를 관찰한 뒤 선행 취소·삭제 트랜잭션을 커밋했다. 수정 요청은 409·404이며 기존 요청사항을 유지했다.
- flush 후 강제 롤백 시 요청사항·Audit가 함께 원복됐다. 모든 수정 HTTP 테스트에서 회사·배송·알림 포트를 호출하지 않았다.
- 별도 Gateway·JWT·Order JAR·PostgreSQL 16에서도 200·400·401·403·404·409를 확인했다. 위조 사용자 헤더로 타인 주문을 수정하지 못했다.
- 인증용 user API 응답은 HTTP 테스트 대역이다. 팀 서비스의 실제 계정·업무 데이터 검증과는 구분한다. 실제 Slack 메시지를 보내지 않았고 임시 Java 프로세스·DB 컨테이너를 정리했다.
- 격리 실행 근거: 임시 `logistics-order-update-smoke-mil9_bf7/result.json`과 서버 로그.
- 로컬 반영: `docker compose build order-service` 후 `docker compose up -d --no-deps --no-build --wait --wait-timeout 90 order-service`를 실행했다. 새 이미지 일치·health UP, Gateway OpenAPI 200/O-04·필수 requestNote/최대 500·미인증 PATCH 401을 확인했다.
- 교체된 컨테이너는 order-service뿐이다. 다른 8개 컨테이너 ID·시작 시각, PostgreSQL 볼륨 연결, .env 해시·Webhook 값이 유지됐다. 기존 Compose DB에는 검증용 주문을 쓰지 않았다.
- 문서 동기화·에러 코드 16개 중복/누락·공백·로컬 링크 검사를 통과했다. 실제 Webhook 값이 변경 파일과 최근 주문 로그에 없음을 확인했다.
- 기존 주문 노트를 주제별 3개 파일로 나누고 링크만 조정했다. 링크 경로를 원복해 비교하면 과거 본문은 원본과 같다. 별도 작업의 Q&A 노트 3개 변경은 그대로 보존했다.

## 직접 해보기

1. Docker를 실행하고 저장소 터미널에서 `./gradlew :order-service:test --tests '*OrderRequestNoteTest' --tests '*OrderUpdate*'`를 실행한다. 39개가 통과하면 O-04 검증을 재현한 것이다.
2. IntelliJ에서는 `OrderRequestNoteTest`, `OrderUpdateServiceTest`, `OrderUpdateControllerWebMvcTest`, `OrderUpdateHttpIntegrationTest` 클래스 옆 실행 버튼을 누른다. DB 테스트는 Docker가 필요하다.
3. `OrderService.updateRequestNote`를 읽고 조회·권한 확인·상태 검증·변경·flush·결과 변환 순서를 찾는다. `OrderQueryService`의 readOnly 조회와 비교한다.
4. Gateway Swagger에서 O-04와 requestNote 필수·최대 길이를 확인한다. 사용할 테스트 주문과 토큰이 있다면 위 PATCH를 보낸 뒤 O-03으로 같은 주문을 읽어 요청사항과 updatedAt을 비교한다.
5. 다른 USER의 토큰은 403, 취소 주문은 409, 빈 요청사항은 400이어야 한다. 상품·수량·단가·총액·배송 ID는 수정 전과 같아야 한다.

## 참고

- [SA 6.7·8.5](../../../SA/sparta-logistics-sa.md)
- [domain 규칙](../../../agents/rules/domain.md), [application 규칙](../../../agents/rules/service.md), [잠금 규칙](../../../agents/rules/repository.md)
- [서비스 구현 노트](../../../services/order/notes.md), [브랜치 진행 상황](../../../progress/feat-order-create.md)
