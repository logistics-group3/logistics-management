# 41. 주문 검색과 단건 조회 API

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `af0ba34 docs(order): Slack 실제 연동과 설정 검증 기록`
- 이번 변경 커밋: `9a41925 feat(order): 주문 검색과 단건 조회 구현`

## 구현한 API

| API | 권한과 결과 |
|---|---|
| O-02 `GET /api/v1/orders` | USER는 본인 주문, MASTER는 전체 주문. `ApiResponse<PageResponse<OrderResponse>>` |
| O-03 `GET /api/v1/orders/{orderId}` | 주문자 본인 또는 MASTER. 없는 주문·삭제된 주문은 404 ORDER_NOT_FOUND, 타인 주문은 403 FORBIDDEN |

검색 필터는 `status`, `supplierCompanyId`, `receiverCompanyId`, `productId`, `startDate`, `endDate`다. 모두 선택 항목이며 함께 주면 AND로 적용한다.
날짜 형식은 `yyyy-MM-dd`이며 시작일·종료일을 포함한다. 시작일이 종료일보다 늦거나 연도가 0001~9999 범위를 벗어나면 400 INVALID_REQUEST다. UUID·날짜·상태의 형식 오류는 400 VALIDATION_ERROR다.
페이지는 0부터 시작한다. size는 10·30·50이며 나머지는 10으로 보정한다. 정렬은 createdAt·quantity만 허용하고 기본값은 createdAt,DESC다.

## 만든 것과 역할

| 파일·타입 | 역할 |
|---|---|
| `presentation/dto/OrderSearchRequest` | HTTP 파라미터를 날짜·UUID·enum으로 바인딩 |
| `application/dto/OrderSearchCondition` | 사용자 검색 조건 검증, 날짜를 저장소 범위로 변환 |
| `application/OrderQueryService` | 조회 트랜잭션과 인증 사용자의 조회 범위 결정 |
| `domain/OrderSearchCriteria` | 서버가 정한 주문자 범위와 변환된 시간 구간을 보관 |
| `domain/OrderQueryRepository` | 동적 검색의 저장소 계약 |
| `infrastructure/persistence/OrderQueryRepositoryImpl` | QueryDSL where·정렬·페이징·count 구현 |
| `domain/OrderErrorCode` | ORDER_NOT_FOUND 정의 |
| 기존 `OrderController`, `OrderResult`, `OrderResponse` | GET 경로 추가와 기존 스냅샷 응답 형식 재사용 |

단건 조회는 기존 OrderRepository.findById를 재사용한다. 테이블과 인덱스는 기존 구조를 사용하며 새 마이그레이션은 없다.

## 왜 이렇게 만들었는가

**1. 본인 주문 조건을 SQL에 넣는다.** USER의 ordererId는 인증 정보로 정한다. HTTP 검색 DTO에는 이 필드를 두지 않으므로 다른 주문자 ID를 요청해도 범위가 넓어지지 않는다.
조회 후 메모리에서 다른 사용자의 주문을 제거하면 페이지가 비거나 전체 건수에서 타인의 주문 수가 드러날 수 있다. 내용 쿼리와 count 쿼리에 같은 조건을 사용한다.

**2. 조회 서비스는 readOnly 트랜잭션으로 묶는다.** 조회 의도를 명시하고 JPA/Hibernate의 변경 감지·flush 최적화를 활용한다. 이 플래그가 소유자 권한 검사를 대신하지는 않는다.
상품명·가격은 주문 당시 스냅샷을 반환한다. 주문 QueryService는 회사·배송·Slack을 호출하지 않는다. Gateway의 인증용 사용자 확인은 별도다.

**3. 종료일은 다음 날 0시 미만으로 바꾼다.** 10월 15일을 조회하면 `createdAt >= 10월 15일 00:00`과 `createdAt < 10월 16일 00:00`을 사용한다.
23:59:59로 잘라 마지막 소수 초를 놓치지 않으며, 날짜 컬럼을 함수로 감싸지 않아 기존 인덱스를 사용할 수 있는 비교 형태를 유지한다.

**4. 정렬과 페이징은 명세를 고정한다.** QuerydslSupport의 허용 목록으로 정렬 경로를 제한한다. 정렬 값이 같은 행은 내부적으로 주문 ID 오름차순을 추가해 동일 데이터에서 페이지 순서를 고정한다.
PageableExecutionUtils는 결과 개수만으로 전체 건수를 알 수 있는 경우 count를 생략한다. 그 외에는 같은 검색 조건으로 실제 전체 건수를 계산한다.

**5. API 입력과 저장소 조건을 구분한다.** SearchCondition에는 사용자가 선택한 필터가 있고, SearchCriteria에는 인증으로 결정한 ordererId와 변환된 시간 경계가 있다. domain이 application DTO에 의존하지 않게 한다.
Controller는 검증·변환·위임을 담당하며 검색 SQL이나 소유자 판단을 갖지 않는다.

## 검증

- 신규 application 10개·MVC 12개·실제 HTTP/PostgreSQL 22개를 추가했다. 기존 테스트를 포함해 주문 모듈 **152개** 통과.
- `./gradlew build --console=plain` 성공. 전체 테스트 XML 합계 **250개**, 실패·오류·skip 0. 주문 테스트는 다시 실행했고 다른 모듈은 기존 성공 결과를 재사용했다.
- 본인 주문 내용·count·다음 페이지, MASTER 조회, 필터 조합, 날짜 경계·한쪽 날짜, 삭제 제외, 동률·복수 정렬, 빈 페이지, 잘못된 조건·정렬, 응답 스냅샷을 검증했다.
- 조회 테스트에서 회사·배송·알림 포트 호출이 없음을 확인했다. 실제 Slack 메시지는 추가로 보내지 않았다.
- 별도 Gateway·JWT·Order JAR·PostgreSQL 16 환경에서 200·400·401·403·404를 확인했다. 위조 X-User 헤더를 보내도 USER의 범위가 유지됐다. 인증용 user API 응답만 HTTP 테스트 대역이다.
- 격리 검증용 Java 프로세스와 DB 컨테이너를 정리했다. 증거 파일은 임시 `logistics-order-query-smoke-qce84k57/result.json`과 서버 로그다.
- 현재 Compose에서도 `docker compose build order-service` 후 `up -d --no-deps --no-build --wait --wait-timeout 90 order-service`를 실행했다. 주문 health UP, Gateway의 O-02·O-03 OpenAPI 200, 기본 size=10·sort=createdAt,DESC와 조회 두 경로의 미인증 401을 확인했다.
- 교체된 컨테이너는 order-service뿐이며 나머지 8개의 ID·시작 시각은 같다. `.env` 해시와 주문 컨테이너의 기존 Webhook 값도 유지됐다.
- OpenAPI size 기본값 불일치는 TS-010, 공유 테스트 DB의 fixture 간섭은 TS-011에 기록했다.
- 문서 동기화·에러 코드 16개 중복/누락·공백·로컬 링크 검사를 통과했다. 실제 Webhook 값은 변경 파일에 없다. 다른 작업에서 추가된 Q&A 학습 노트는 보존했다.

## 직접 해보기

1. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderQuery*'`를 실행한다. IntelliJ에서는 각 테스트 클래스 옆 실행 버튼을 누른다.
2. 전체 주문 회귀 검증은 `./gradlew :order-service:test`, 전체 빌드는 `./gradlew build`로 실행한다. 마지막 전체 검증은 152개·250개 기준이다.
3. Gateway Swagger의 order-service에서 O-02와 O-03을 확인한다. O-02의 기본 size가 10이고 사용자 ID 입력 항목이 없는지 본다.
4. 유효한 토큰과 테스트 주문 데이터가 있다면 아래 검색을 실행한다. 다른 사용자의 주문 ID로 단건 조회하면 USER는 403, MASTER는 200이어야 한다.

```http
GET /api/v1/orders?status=ORDERED&startDate=2026-10-01&endDate=2026-10-31&page=0&size=10&sort=createdAt,DESC
GET /api/v1/orders/{orderId}
```

## 참고

- [SA 6.7](../../../SA/sparta-logistics-sa.md), [application 규칙](../../../agents/rules/service.md), [검색 저장소 규칙](../../../agents/rules/repository.md)
- [Spring Data JPA 3.5 트랜잭션·readOnly](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/transactions.html)
- [문제 해결 기록](../../../troubleshooting.md)
