# 스텝 10-2. Feign 공통 오류 처리와 재시도 (리뷰 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 1](review-01-common-infra.md) minor 8(Feign ErrorDecoder 없음), minor 9(`@EnableFeignClients` 위치)
- 결과: common 테스트 24개 통과, 전체 빌드 통과

## 무엇을 고쳤나
| 문제 | 수정 |
|---|---|
| 하위 서비스가 4xx·5xx를 돌려주면 `FeignException`이 공통 처리기의 마지막 단계로 흘러 500이 됨(SA 3.5는 도메인 예외 또는 503) | `FeignErrorDecoder`(common, 모든 FeignClient에 자동 적용): 4xx이면서 SA 6.1 형식이면 상태·코드·문구를 그대로 `BusinessException`에 담는다. 5xx나 형식을 알 수 없으면 503 SERVICE_UNAVAILABLE |
| 연결 실패·타임아웃(`RetryableException`)은 응답이 없어 디코더를 거치지 않음 | `GlobalExceptionHandler`에 `FeignException` 처리기 추가 → 503, WARN 로그 |
| SA 3.5 재시도 규칙(GET과 멱등 API만 최대 3회)을 쓸 방법이 없음 | `IdempotentRetryConfig`: 필요한 클라이언트에만 붙이는 opt-in 설정 |
| `@EnableFeignClients`가 메인 클래스에 있어, FeignClient가 생기면 `@WebMvcTest`가 깨질 수 있음 | 서비스마다 `infrastructure/config/FeignConfig`로 옮김(`basePackages`로 서비스 패키지 지정) |

## 리뷰 포인트
1. **도메인 코드 전달**: order가 company를 불러 404 COMPANY_NOT_FOUND를 받으면, 클라이언트도 같은 404 COMPANY_NOT_FOUND를 받는다. 서비스마다 하위 오류를 다시 매핑하지 않아도 된다. 바꿔야 할 때만 서비스에서 `BusinessException`을 잡아 다른 코드로 던진다.
2. **재시도를 전역으로 두지 않은 이유**: 타임아웃은 "요청이 처리됐는지 모르는" 상태다. 주문 생성 같은 POST를 다시 보내면 중복 주문이 생긴다. 그래서 `IdempotentRetryConfig`에는 일부러 `@Configuration`을 붙이지 않았다. 붙이면 컴포넌트 스캔에 걸려 모든 클라이언트에 적용된다.
3. **슬라이스 테스트와 설정 분리**: `@WebMvcTest`는 웹 계층 빈만 올리고, 컴포넌트 스캔으로 찾은 일반 `@Configuration`은 뺀다. 메인 클래스의 어노테이션은 항상 따라 들어오므로, Feign·JPA Auditing처럼 웹과 무관한 설정은 별도 클래스로 둔다. 같은 이유로 `JpaAuditingConfig`도 이미 별도 클래스다.

## 서비스에서 쓰는 법
```java
@FeignClient(name = "hub-service", configuration = IdempotentRetryConfig.class)
public interface HubClient {
    @GetMapping("/internal/hubs/{hubId}")
    ApiResponse<HubResult> getHub(@PathVariable UUID hubId);
}
// 404 HUB_NOT_FOUND → BusinessException(404, HUB_NOT_FOUND)로 그대로 올라온다
// 연결 실패·타임아웃 → 3회 재시도 후 503 SERVICE_UNAVAILABLE
```

## 면접 포인트
- 서비스 간 호출에서 재시도해도 되는 요청과 안 되는 요청(멱등성)
- 하위 서비스 장애를 호출자에게 어떻게 전달할지(코드 전달 대 503 변환)
