---
paths:
  - "**/src/test/**"
---
# 테스트 규칙

적용 경로: `**/src/test/**`. 기본 규칙은 `docs/agents/coding-conventions.md` 4절이다.

## 무엇을 어떻게
| 대상 | 방식 | 예 |
|---|---|---|
| Entity 도메인 메서드 | Spring 없는 단위 테스트. 불변식(상태 전이 등)이 있으면 필수 | `BaseEntityTest` |
| application 서비스 | `@ExtendWith(MockitoExtension.class)`, Repository·외부 호출 목. 새 public 메서드마다 성공 1개와 실패 케이스 | `AuthServiceTest` |
| DB 동작(제약, 네이티브 쿼리, 부분 인덱스, 동시성) | `PostgresIntegrationTest` 상속(Testcontainers PostgreSQL 16) | `UserAuthIntegrationTest` |
| 컨트롤러 | `MockMvcBuilders.standaloneSetup` 또는 `@WebMvcTest`(common 웹 설정이 자동 포함) | `AuthControllerTest`, `AuthControllerWebMvcTest` |

- H2를 쓰지 않는다. DB 테스트는 Docker가 켜져 있어야 하고 기본 `./gradlew build`에 포함된다.
- 통합 테스트는 같은 애너테이션과 속성을 쓰면 Spring 컨텍스트를 재사용한다. 테스트마다 속성·`@MockitoBean`·`@Import`를 바꾸면 컨텍스트가 새로 뜬다.
- 컨텍스트마다 Hikari 풀이 DB 연결을 약 10개 쥐고, 모듈의 모든 통합 테스트가 PostgreSQL 컨테이너 한 개(`max_connections` 100, 일반 연결 97)를 공유한다. 새 컨텍스트가 연결을 못 얻으면 `too many clients already`로 그 클래스 전체가 실패한다(TS-025).
- `common` 테스트 지원의 `spring.properties`가 캐시 컨텍스트를 8개로 제한해 오래된 컨텍스트의 풀을 닫는다(최악 8 x 10 = 80). 상한을 올리거나 컨텍스트 변형을 크게 늘리기 전에 `script/test-db-connections.sh :모듈:test`로 최대 연결 수를 확인한다.

## 형식
```java
@Test
@DisplayName("집하가 시작된 배송은 취소할 수 없다")
void cancelAfterPickupStarted() {
    Delivery delivery = deliveryInStatus(DeliveryStatus.PICKING_UP);

    assertThatThrownBy(delivery::cancel)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);
}
```
- `@DisplayName`은 한국어로 규칙을 쓴다("~하면 ~한다").
- 목은 BDDMockito(`given(...).willReturn(...)`), 단언은 AssertJ를 쓴다.
- 테스트 데이터는 private 헬퍼나 Entity 정적 팩토리로 만든다. ID처럼 불가피할 때만 `ReflectionTestUtils`를 쓴다.
- 검증을 약하게 바꿔서 테스트를 통과시키지 않는다.
