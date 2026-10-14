# 스텝 15. 소유자 검증 헬퍼

- 날짜: 2026-10-14
- 근거: Notion "팀 합의 대조·추가 검토" 2장 (haneol 브랜치 `UserContext` 헬퍼, 팀 의견 "반영하면 좋을 것 같습니다")
- 결과: `UserContextTest` 5개 통과, 기존 `UserContextWebTest` 7개 통과

## 바꾼 것
| 메서드 | 동작 |
|---|---|
| `isSelf(UUID)` | 현재 사용자 ID와 같으면 true. null이면 false |
| `isSelfOrMaster(UUID)` | MASTER이거나 본인이면 true |
| `validateMaster()` | MASTER가 아니면 `BusinessException(FORBIDDEN)` |
| `validateSelfOrMaster(UUID)` | 본인도 MASTER도 아니면 `BusinessException(FORBIDDEN)` |

## 리뷰 포인트
1. **역할 검사와 소유자 검사의 위치**: "MASTER만 호출 가능"은 요청만 보고 판단할 수 있어 컨트롤러의 `@RequireRole`이 맡는다. "주문자 본인 또는 MASTER"는 DB에서 주문을 읽어야 알 수 있어 서비스 계층이 이 헬퍼로 검사한다(SA 8.3의 7번).
2. **JSON 직렬화 주의**: `UserContext`는 샘플 API 응답으로 그대로 나간다. Jackson은 인자 없는 `isXxx()`를 속성으로 내보내므로 `isMaster()`에는 `@JsonIgnore`가 붙어 있다. 새 헬퍼는 인자가 있거나 `validate`로 시작해 직렬화되지 않는다. 테스트로 응답 속성이 늘지 않았음을 확인했다.
3. **haneol 원본과 다른 점**: 원본 `UserContext`에는 `isDeliveryManager()`도 있었다. 통합안은 record 접근자 `deliveryManager()`가 이미 있어 추가하지 않았다.

## 사용 예
```java
public OrderResult cancel(UUID orderId, UserContext user) {
    Order order = orderRepository.getById(orderId);
    user.validateSelfOrMaster(order.getOrdererId());
    ...
}
```
