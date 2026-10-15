# 38. 주문 Feign 어댑터와 Controller 연결

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 이전 작업 커밋: `3b5a39c feat(order): 주문 생성 흐름과 실패 보상 구현`
- 구현 커밋: `9b48adc feat(order): 주문 생성 Feign과 Controller 연결`

## 만든 것

| 위치 | 역할 |
|---|---|
| `infrastructure/client/CompanyClient`, `CompanyClientAdapter` | I-07·I-08 HTTP 조회와 application 계약 연결 |
| `DeliveryCreateClient`, `DeliveryDeleteClient`, `DeliveryClientAdapter` | I-09·I-13 연결, 생성·삭제 재시도 분리 |
| `infrastructure/client/dto/*` | 상대 서비스와 주고받는 JSON 계약 |
| `infrastructure/client/config/DeliveryDeleteFeignConfig` | 삭제 Feign의 재시도를 꺼 보상 루프와 중복 방지 |
| `infrastructure/config/OrderJacksonConfig` | 소수 수량·단가가 정수로 잘리는 변환 차단 |
| `presentation/OrderController`, `presentation/dto/*` | 입력 검증·Command 변환·201 응답 DTO 변환 |

`OrderService`를 `@Service`로 등록해 실제 어댑터를 주입한다. 기존 통합 테스트의 명시적 `@Import`는 제거했다.

## 왜 이렇게 연결했는가

Controller는 HTTP 계약을, application은 업무 흐름을, Feign 어댑터는 외부 통신과 전송 형식을 맡는다. 그래서 외부 API 필드나 응답 형식이 바뀌어도 주문 로직에 바로 퍼지지 않는다.
같은 delivery 서버를 호출하더라도 생성과 삭제는 재시도 책임이 달라 contextId를 나눴다. 생성은 Feign에서 재시도하고, 삭제는 OrderService의 보상 루프에서만 재시도한다.

가격을 전송 DTO에서는 `Long`으로 받아 필드 누락과 0원을 구분한다. 검증 후 application과 Entity에는 기존 long을 전달한다.
HTTP 테스트에서는 Feign 자체를 Mock으로 바꾸지 않고 실제 요청을 보내 JSON·헤더·재시도 횟수를 확인한다.

## 검증

- Controller 11개, 실제 HTTP/Feign 13개가 추가돼 주문 모듈은 93개다. 모두 통과했다.
- `./gradlew build --console=plain` 성공. 전체 테스트 XML 합계 191개, 실패·오류·skip 0. 주문 테스트는 실행했고 다른 모듈은 이전 결과를 재사용했다.
- 문서 동기화·에러 코드·공백·문서 로컬 링크 검사도 통과했다.
- 현재 Compose: 주문 서비스 재기동, Flyway V1 적용, Gateway OpenAPI 200/O-01 노출, 인증 없는 요청 401을 확인했다.
- 별도 임시 환경: 실제 Gateway·JWT·Order·Feign·PostgreSQL로 401·201·400·503, 위조 헤더 제거, Audit, 배송 보상·미저장을 확인했다. user·company·delivery 응답은 HTTP 대역이며, 임시 프로세스와 DB 컨테이너를 정리했다.
- 팀 서비스의 실제 업무 데이터로 정상 주문 생성, Slack 발송은 미검증이다.

## 직접 해보기

1. `./gradlew :order-service:test --tests '*OrderControllerWebMvcTest'`로 입력·응답 계약 11개를 확인한다. IntelliJ에서는 클래스 옆 실행 버튼을 사용한다.
2. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderHttpIntegrationTest'`로 실제 HTTP 13개를 확인한다. 생성 재시도는 같은 orderId를 쓰고, 삭제 HTTP는 9회가 아닌 3회인지 본다.
3. `./gradlew :order-service:test`로 전체 93개를 실행한 뒤 `order-service/build/reports/tests/test/index.html`을 연다.
4. 로컬 Gateway Swagger에서 order-service의 O-01을 확인한다. 유효한 토큰과 실제 업체·상품·배송 API가 준비돼야 현재 Compose에서 정상 주문을 만들 수 있다.

## 로컬 실행 주의점

이번 `script/run.sh order-service`는 PostgreSQL도 재생성했다. 기존 볼륨은 재사용됐고 초기화는 생략됐다. `.env`와 나머지 7개 컨테이너는 유지됐으며 관련 서비스 health는 UP이었다.
의존 서비스가 이미 정상이고 주문만 교체하려면 빌드 후 `docker compose up -d --no-deps --no-build order-service`로 범위를 지정한다. 공동 스크립트는 이번에 변경하지 않았다.

## 다음 단계

커밋 후 비동기 Slack 알림을 구현하고, 팀 company·delivery 구현과 실제 데이터로 통합 검증한다. 상세 구현·실행 근거는 [order 구현 노트](../../../services/order/notes.md)에 있다.
테스트 드라이버의 503 재전송과 의존 DB 재생성 기록은 [TS-007·TS-008](../../../troubleshooting.md)에 있다.
