---
paths:
  - "backend/**/infrastructure/client/**"
---
# FeignClient 규칙 (서비스 간 호출)

## 구조
```
application/HubStockPort.java                     ← application이 의존하는 인터페이스
infrastructure/client/HubClient.java              ← @FeignClient
infrastructure/client/HubStockAdapter.java        ← HubStockPort 구현, HubClient 호출
infrastructure/client/dto/DecreaseStockRequest.java, HubStockResponse.java
```
```java
@FeignClient(name = "hub-service", path = "/internal/hubs")
public interface HubClient {

    /** I-05 재고 차감 (제공: hub-service) */
    @PostMapping("/{hubId}/stocks/decrease")
    HubStockResponse decreaseStock(@PathVariable UUID hubId, @RequestBody DecreaseStockRequest request);
}
```
- `name`은 Eureka 서비스 이름(`spring.application.name`). URL 하드코딩 금지
- 호출 대상은 상대 서비스의 `/internal/**`만. 외부 API(`/api/v1/**`)를 Feign으로 부르지 않는다
- 응답은 래퍼 없는 DTO (ADR-0005). DTO는 `client/dto`에 필요한 필드만 record로 복사 — 상대 서비스 클래스를 import하지 않는다
- Application 클래스에 `@EnableFeignClients`가 있는지 확인

## 공통 처리 (common이 자동으로 함, 직접 구현 금지)
- X-User-* 헤더 전달 (`FeignHeaderInterceptor`) → 상대 서비스의 Audit `created_by`가 원래 사용자로 남는다
- 에러 변환 (`FeignErrorDecoder`): 상대 4xx → 같은 `code`의 `BusinessException`, 5xx·타임아웃 → 503 `SERVICE_UNAVAILABLE`
- 타임아웃 connect 2s / read 5s (`config/application.yml`)

## 에러 분기
```java
try {
    hubClient.decreaseStock(hubId, request);
} catch (BusinessException e) {
    if (e.hasCode(RemoteCodes.STOCK_INSUFFICIENT)) { ... }   // 보상 또는 사용자 에러
    throw e;
}
```
- 분기는 Adapter 또는 application에서. Feign 인터페이스에 로직 금지
- 재시도는 멱등 API(조회)만. 재고 차감 같은 변경 API는 재시도하지 않고 보상으로 처리

## 문서
호출 관계를 추가하면 `doc/design/02-architecture.md` §5 표(호출자 → 제공자, 내부 API, 상태)와 `04-api.md` 내부 API 절.
