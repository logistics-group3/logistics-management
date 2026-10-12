---
paths:
  - "backend/**/src/test/**"
---
# 테스트 규칙

참고 구현: `user-service/.../AuthServiceTest.java`, `common/.../RoleCheckInterceptorTest.java`.

## 무엇을
| 대상 | 방식 | 필수 여부 |
|---|---|---|
| Application Service | 단위 테스트 (`@ExtendWith(MockitoExtension.class)`, Repository·Port mock) | 새 public 메서드마다 성공 1 + 실패 케이스 |
| Entity 도메인 메서드 | 순수 단위 테스트 (Spring 없이) | 불변식이 있으면 필수 (재고 음수, 상태 전이) |
| QueryDSL 검색 | `@DataJpaTest` + Testcontainers 또는 생략 | 선택 |
| Controller | `@WebMvcTest` | 선택 (권한·검증이 복잡할 때) |

## 형식
```java
@Test
@DisplayName("재고보다 많이 차감하면 STOCK_INSUFFICIENT")
void decreaseStock_insufficient() {
    // given
    given(hubStockRepository.findByIdForUpdate(stockId)).willReturn(Optional.of(stock(5)));

    // when & then
    assertThatThrownBy(() -> hubStockService.decrease(stockId, 10))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(HubErrorCode.STOCK_INSUFFICIENT));
}
```
- `@DisplayName`은 한국어로 **규칙**을 쓴다 ("~하면 ~된다")
- `// given` `// when` `// then` 주석으로 구분
- mock: BDDMockito `given(...).willReturn(...)`, 검증 `verify(..., never())`
- 단언: AssertJ (`assertThat`, `assertThatThrownBy`). JUnit `assertEquals` 쓰지 않는다
- 테스트 클래스·메서드는 package-private, 메서드 이름은 `{대상}_{상황}`
- 테스트 데이터 생성은 private 헬퍼 메서드 또는 Entity 정적 팩토리. 리플렉션으로 필드 세팅은 ID 등 불가피할 때만 (`ReflectionTestUtils`)
- 외부 서비스·DB에 의존하는 테스트를 기본 `./gradlew build`에 넣지 않는다 (CI·로컬 모두 DB 없이 통과)
