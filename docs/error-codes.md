# 에러 코드 목록

모든 서비스의 에러 코드를 한곳에서 본다. 응답 형식은 SA 6.1, 이름 규칙은 `docs/agents/coding-conventions.md` 2절을 따른다.
코드를 추가하거나 바꾸면 이 표도 함께 고친다. `script/error-codes-check.sh`가 서비스 사이 중복과 이 표의 누락을 검사한다.

- 상태: `구현`은 코드에 정의돼 있음, `예정`은 SA에 정의됐고 담당 서비스가 구현할 코드
- 하위 서비스의 4xx 코드는 `FeignErrorDecoder`가 그대로 클라이언트에 전달한다. 그래서 이름이 겹치면 어느 서비스의 실패인지 구분할 수 없다.

## 공통 (`common-core` `CommonErrorCode`)
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 요청 값 검증 실패. `errors[]`에 필드별 사유 | 구현 |
| `INVALID_REQUEST` | 400 | 비즈니스 규칙 위반 | 구현 |
| `UNAUTHORIZED` | 401 | 토큰 누락, 위조, 만료 | 구현 |
| `TOKEN_ROLE_MISMATCH` | 401 | 토큰의 권한과 저장된 권한이 다름(재로그인 필요) | 구현 |
| `FORBIDDEN` | 403 | 권한 없음(MASTER 전용 API, 소유자 아님) | 구현 |
| `NOT_FOUND` | 404 | 경로 없음, 외부에서 `/internal/**` 호출 | 구현 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 | 구현 |
| `NOT_ACCEPTABLE` | 406 | 지원하지 않는 응답 형식 | 구현 |
| `INVALID_STATUS_TRANSITION` | 409 | 허용되지 않는 상태 변경(order 주문 취소, delivery 구간 상태) | 구현 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 Content-Type | 구현 |
| `INTERNAL_SERVER_ERROR` | 500 | 처리하지 못한 예외 | 구현 |
| `SERVICE_UNAVAILABLE` | 503 | 하위 서비스 호출 실패·타임아웃 | 구현 |

## user-service (`UserErrorCode`)
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `DUPLICATE_USERNAME` | 409 | username 중복(탈퇴자 포함) | 구현 |
| `LOGIN_FAILED` | 401 | 아이디 또는 비밀번호 불일치 | 구현 |
| `USER_NOT_FOUND` | 404 | 사용자가 없거나 탈퇴함 | 구현 |

## hub-service
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `HUB_NOT_FOUND` | 404 | 허브가 없음 | 예정 |
| `HUB_ROUTE_NOT_FOUND` | 404 | 허브 간 경로가 없음 | 예정 |

## company-service
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `COMPANY_NOT_FOUND` | 404 | 업체가 없거나 삭제됨 | 예정 |
| `PRODUCT_NOT_FOUND` | 404 | 상품이 없거나 삭제됨 | 예정 |

## order-service
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 주문이 없거나 삭제됨 | 예정 |
| `ORDER_NOT_DELETABLE` | 409 | 취소 또는 배송 완료 전 주문 삭제 | 예정 |

## delivery-service (`DeliveryErrorCode`)
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `DELIVERY_NOT_FOUND` | 404 | 배송이 없음 | 구현 |
| `DELIVERY_ROUTE_NOT_FOUND` | 404 | 배송 구간이 없음 | 구현 |
| `DELIVERY_MANAGER_NOT_FOUND` | 404 | 배정할 배송담당자가 없음, 재배정 대상이 배송담당자가 아님 | 구현 |
| `MANAGER_HUB_MISMATCH` | 400 | 재배정할 배송담당자의 소속 허브가 구간 담당 허브와 다름 | 구현 |
