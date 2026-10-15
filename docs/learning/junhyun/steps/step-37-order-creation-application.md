# 37. 주문 생성 application 흐름과 실패 보상

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 기반 커밋: `216a768 feat(order): 주문 엔티티와 저장소 기반 구현`
- 현재 변경: application 구현·테스트·문서, 미커밋

## 만든 것

| 위치 | 역할 |
|---|---|
| `application/OrderService` | 업체·상품·권한 검증, 배송 생성, 주문 저장, 실패 보상 |
| `application/OrderWriter` | 주문 저장과 결과 변환을 별도 로컬 트랜잭션으로 처리 |
| `application/CompanyReader`, `DeliveryGateway` | 외부 서비스 호출 계약 |
| `application/dto/*` | 입력·조회 결과·배송 생성 정보·주문 결과 record |
| `CreateOrderCommandTest`, `OrderServiceTest` | 입력·권한·생성·실패 분기 23개 |
| `OrderCreationIntegrationTest` | 실제 PostgreSQL 커밋과 실패 보상 4개 |

## 왜 나눴는가

`OrderService`는 업무 순서를 지휘하고, `OrderWriter`만 DB 트랜잭션을 연다. 원격 API를 기다리는 동안 주문 DB 트랜잭션을 유지하지 않기 위한 구분이다.
별도 빈의 트랜잭션 프록시가 커밋한 뒤 `OrderWriter.save` 호출이 끝나므로, flush 뒤 커밋에서 발생하는 오류도 `OrderService`가 잡아 배송을 보상한다.

회사·상품 조회와 배송담당자 없음은 데이터를 바꾸기 전에 실패한다. 반면 배송 타임아웃은 서버에서 배송을 만들었는지 알 수 없어, 선발급 orderId로 삭제 보상을 요청한다.
배송 삭제는 총 3회 시도하고 끝까지 실패하면 주문 ID와 실패 상태를 로그에 남긴다. 최초 오류는 보상 오류로 덮어쓰지 않는다.

## 검증

- 테스트를 먼저 작성해 타입 누락 실패를 확인했다. 이후 Mockito 검증 중첩 오류를 해결하고 application 단위 테스트 23개를 통과했다.
- 실제 PostgreSQL에서 정상 커밋·flush 실패·커밋 실패·트랜잭션 경계 테스트 4개가 통과했다.
- `./gradlew :order-service:test --console=plain`: 기존 기반 42개를 포함한 69개 통과, 실패·오류·skip 0.
- `./gradlew build --console=plain`: 성공. 전체 XML 합계 167개, 실패·오류·skip 0. 기존 성공 테스트 결과를 재사용했다.
- 문서 동기화·에러 코드·공백·문서 로컬 링크 검사도 통과했다.

## 직접 해보기

1. `./gradlew :order-service:test --tests 'com.sparta.logistics.order.application.*'`를 실행한다. 23개가 통과하는지 확인한다. IntelliJ에서는 두 테스트 클래스 옆 실행 버튼으로 확인한다.
2. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderCreationIntegrationTest'`를 실행한다. `compensatesFailureAtCommit`은 테스트용 지연 제약으로 flush 이후 commit을 실패시킨다.
3. `OrderService.create`의 `NEVER`와 `OrderWriter.save`의 `REQUIRES_NEW`를 읽고, 배송 생성·삭제 Mock 안의 트랜잭션 검증과 연결해 본다.
4. 전체 주문 리포트는 필터 없이 `./gradlew :order-service:test`를 실행한 뒤 `order-service/build/reports/tests/test/index.html`에서 확인한다.

## 다음 단계

실제 company·delivery Feign 어댑터와 OrderService의 Spring 빈 등록을 연결한다. I-13은 서비스에서 재시도하므로 어댑터에서 다시 재시도하지 않는다.
Controller·Request·Response를 추가하고 Gateway를 통한 정상·오류 호출을 검증한 뒤 커밋 후 Slack 알림으로 이어간다. 현재는 외부 서비스를 Mock으로 검증한 상태다.

## 기록

- [order 구현 노트와 공식 근거](../../../services/order/notes.md)
- [Mockito 검증 중첩 문제 TS-006](../../../troubleshooting.md)
