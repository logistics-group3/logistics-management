---
paths:
  - "*-service/src/main/java/**/infrastructure/client/**"
---
# FeignClient 규칙 (서비스 간 호출)

적용 경로: `*-service/src/main/java/**/infrastructure/client/**`

## 구조
```
application/CompanyReader.java                 ← application이 쓰는 인터페이스
infrastructure/client/CompanyClient.java       ← @FeignClient
infrastructure/client/CompanyClientAdapter.java ← CompanyReader 구현
infrastructure/client/dto/CompanyResponse.java ← 필요한 필드만 복사한 record
```
```java
@FeignClient(name = "company-service", path = "/internal")
public interface CompanyClient {

    /** I-07 업체 조회 (제공: company-service) */
    @GetMapping("/companies/{companyId}")
    CompanyResponse getCompany(@PathVariable UUID companyId);
}
```
- `name`은 Eureka 서비스 이름(`spring.application.name`)이다. URL을 하드코딩하지 않는다.
- 상대 서비스의 `/internal/**`만 호출한다. 외부 API(`/api/v1/**`)를 Feign으로 부르지 않는다.
- 응답은 감싸지 않은 DTO다(ADR 0010). 상대 서비스 클래스를 import하지 않고 필요한 필드만 `client/dto`에 record로 둔다.
- `@EnableFeignClients`는 각 서비스의 `infrastructure/config/FeignConfig`에 있다.

## common이 하는 일 (직접 만들지 않는다)
- `X-User-*` 헤더 전달(`FeignHeaderInterceptor`). 상대 서비스의 Audit에 원래 사용자가 남는다.
- 에러 변환(`FeignErrorDecoder`). 상대가 4xx면 같은 `code`의 `BusinessException`, 5xx·연결 실패·타임아웃이면 503 `SERVICE_UNAVAILABLE`이다.
- 타임아웃은 connect 5초, read 20초다(`config-repo/application.yml`, SA 3.5).

## 재시도
- GET과 orderId 기반 멱등 API(배송 생성, 취소 원복, 삭제)만 재시도한다(SA 3.5): `@FeignClient(name = "...", configuration = IdempotentRetryConfig.class)`.
- 멱등하지 않은 POST(배송 취소 등)는 재시도하지 않는다. 실패는 보상으로 처리한다.
- 어떤 API가 재시도 대상인지는 상대 서비스의 `docs/services/{service}/README.md`를 본다.

## 에러 분기
```java
try {
    return deliveryClient.create(request);
} catch (BusinessException e) {
    if ("DELIVERY_MANAGER_NOT_FOUND".equals(e.getErrorCode().code())) { ... }
    throw e;
}
```
- 분기는 Adapter나 application에서 한다. Feign 인터페이스에는 로직을 넣지 않는다.

## 문서
호출 관계를 추가하면 내 서비스의 `docs/services/{service}/README.md` "호출하는 다른 서비스 API" 표를 고친다. 상대 API에 변경이 필요하면 그 서비스 협업 로그에 요청한다.
