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
| `INVALID_STATUS_TRANSITION` | 409 | 허용되지 않는 상태 변경(order 취소된 주문 수정·중복 취소·취소 진행/복구 대기, delivery 구간 상태) | 구현 |
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
| `HUB_NOT_FOUND` | 404 | 허브가 없음 | 구현 |
| `HUB_ROUTE_NOT_FOUND` | 404 | 허브 간 경로가 없음 | 구현 |
| `STOCK_INSUFFICIENT` | 409 | 공급업체 허브의 재고가 모자라거나 재고 레코드가 없음(I-05, 고도화) | 구현 |
| `STOCK_ALREADY_RESTORED` | 409 | 이미 복원한 주문을 같은 `orderId`로 다시 할당함(I-05, 고도화) | 구현 |

## company-service (`CompanyErrorCode`)
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `COMPANY_NOT_FOUND` | 404 | 업체가 없거나 삭제됨 | 구현 |
| `PRODUCT_NOT_FOUND` | 404 | 상품이 없거나 삭제됨 | 구현 |

## order-service
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 주문이 없거나 삭제됨 | 구현 |
| `ORDER_IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 사용자·멱등키에 다른 주문 본문 사용 | 구현 |
| `ORDER_CREATION_NOT_FOUND` | 404 | 주문 생성 진행 조회(O-07)의 기록이 없거나 삭제됨 | 구현 |
| `ORDER_CREATION_IN_PROGRESS` | 409 | 같은 키의 주문 생성 처리 중(동기 실행 전용). O-01 HTTP는 처리 중 재요청에 202와 같은 orderId를 돌려주므로 이 코드를 보내지 않는다 | 구현 |
| `ORDER_CREATION_ABORTED` | 409 | 같은 키의 생성이 실패로 종료됨, 재실행 없음 | 구현 |
| `ORDER_CREATION_RECOVERY_REQUIRED` | 409 | 생성 결과 확인 필요 또는 생성 기록 논리 삭제, 재실행 없음 | 구현 |
| `ORDER_NOT_DELETABLE` | 409 | 취소·배송 완료 전 삭제, 미해결 취소 또는 삭제 진행 기록 불일치 | 구현 |

## delivery-service (`DeliveryErrorCode`)
| 코드 | HTTP | 의미 | 상태 |
|---|---|---|---|
| `DELIVERY_NOT_FOUND` | 404 | 배송이 없음 | 구현 |
| `DELIVERY_ROUTE_NOT_FOUND` | 404 | 배송 구간이 없음 | 구현 |
| `DELIVERY_MANAGER_NOT_FOUND` | 404 | 배정할 배송담당자가 없음, 재배정 대상이 배송담당자가 아님 | 구현 |
| `MANAGER_HUB_MISMATCH` | 400 | 재배정할 배송담당자의 소속 허브가 구간 담당 허브와 다름 | 구현 |
| `DELIVERY_CREATION_CLOSED` | 409 | 이미 종료된 배송 생성 시도(I-13이 종료 표시를 남긴 orderId)의 I-09 | 구현 |
