---
title: 서비스별 에러 코드는 어디에 두고, 하위 서비스의 에러는 클라이언트에 어떻게 전달하나
date: 2026-10-15
updated: 2026-10-15
asker: Junhyun An
level: 중급
tags: [exception, error-code, feign, msa, saga]
---

# 서비스별 에러 코드는 어디에 두고, 하위 서비스의 에러는 클라이언트에 어떻게 전달하나

## 질문
특정 서비스에서만 나는 에러는 어떻게 처리되나. 공통 에러가 아닌데 공통으로 빼는 것이 맞나. 주문→배송 과정에서 배송이 실패하면 내부에서는 배송 실패 에러가 나지만, 클라이언트는 주문이 실패했다는 에러를 받아야 할 것 같다.

## 짧은 답
- 공통으로 빼는 것은 에러를 담는 틀(`ErrorCode` 인터페이스, `BusinessException`, `ErrorResponse` 형식, `GlobalExceptionHandler`)이다. 에러 코드 자체는 각 서비스의 enum에 둔다. `CommonErrorCode`에는 여러 서비스가 같은 뜻으로 쓰는 코드만 둔다.
- delivery가 `404 DELIVERY_MANAGER_NOT_FOUND`를 주면 order는 그 코드를 그대로 클라이언트에 전달한다. delivery가 5xx를 주거나 응답이 없으면 배송 삭제로 보상하고 `503 SERVICE_UNAVAILABLE`로 바꾼다.
- "주문이 실패했다"는 사실은 요청 주소(`POST /api/v1/orders`)와 HTTP 상태(2xx가 아님)가 이미 알려 준다. `code`는 "왜 실패했는지"를 알려 주는 칸이다.
- 다만 내부 사정이 그대로 새면 안 되는 에러도 있다. 기준은 "그 에러를 클라이언트가 이해하고 대응할 수 있는가"다. 지금 구조는 order가 delivery에 잘못 보낸 요청(400)까지 그대로 내보내는 빈틈이 있다.

## 자세히

### 1. 공통으로 빼는 것은 틀이고, 코드는 서비스에 둔다
회사의 사고 보고서에 빗대면, 보고서 양식은 회사 공통이고 "사고 사유"는 부서마다 다르다. 양식이 같으니 누가 받아도 같은 방식으로 읽을 수 있다.

| 무엇 | 어디 | 역할 |
|---|---|---|
| `ErrorCode` 인터페이스 | common-core | "에러 코드는 상태, 코드, 문구를 가진다"는 틀 |
| `BusinessException` | common-core | 서비스 코드가 `ErrorCode`를 담아 던지는 예외 |
| `ErrorResponse` | common-core | 클라이언트가 받는 JSON 모양 |
| `GlobalExceptionHandler` | common | 던진 예외를 `ErrorResponse`로 바꾸는 곳(MVC 전용) |
| `CommonErrorCode` | common-core | 여러 서비스가 같은 뜻으로 쓰는 코드. 예: `VALIDATION_ERROR`, order와 delivery가 함께 쓰는 `INVALID_STATUS_TRANSITION` |
| `OrderErrorCode`, `UserErrorCode` 등 | 각 서비스 `domain` 패키지 | 그 서비스만 쓰는 코드. 예: `ORDER_NOT_FOUND`, `DELIVERY_MANAGER_NOT_FOUND` |

`ErrorCode`가 enum 하나가 아니라 인터페이스인 이유가 여기에 있다. common은 서비스 도메인을 몰라도 되고, 각 서비스는 자기 enum을 만들어 같은 예외 처리 흐름에 올라탄다.

서비스 전용 코드를 common으로 옮기는 기준은 "두 서비스 이상이 같은 뜻으로 쓰는가"다. `DELIVERY_MANAGER_NOT_FOUND`는 delivery만 만드는 코드이므로 delivery의 enum에 둔다. order가 이 코드를 클라이언트에 전달하더라도 order가 이 코드를 정의할 필요는 없다(아래 2절).

코드 이름에 리소스 접두어(`DELIVERY_`, `COMPANY_`)를 붙이는 이유도 같다. 하위 서비스의 코드가 그대로 전달되므로, 이름만 보고 어느 서비스에서 실패했는지 알 수 있어야 한다. 서비스 enum끼리 이름이 겹치면 구분할 수 없으므로 `script/error-codes-check.sh`가 중복을 검사한다.

### 2. 주문→배송에서 배송이 실패하면 실제로 일어나는 일
```
클라이언트 ── POST /api/v1/orders ──▶ Gateway ──▶ order-service
                                                   │ POST /internal/deliveries (Feign)
                                                   ▼
                                              delivery-service
                                                   │ 404 {"code":"DELIVERY_MANAGER_NOT_FOUND", ...}
                                                   ▼
            FeignErrorDecoder(common)   4xx이고 SA 형식이면 상태·코드·문구를 BusinessException에 담는다
            OrderService.createDelivery  SERVICE_UNAVAILABLE이 아니면 보상 없이 다시 던진다(저장된 것이 없다)
            GlobalExceptionHandler(common) ErrorResponse로 바꾼다. path는 order가 받은 요청 주소다
                                                   │
클라이언트 ◀── 404 {"code":"DELIVERY_MANAGER_NOT_FOUND", "path":"/api/v1/orders", ...}
```

delivery의 응답에 따라 order가 하는 일은 다음과 같다(SA 7.1).

| delivery 결과 | order 처리 | 클라이언트 응답 |
|---|---|---|
| `404 DELIVERY_MANAGER_NOT_FOUND` | 그대로 전달. delivery가 저장 전에 확인하므로 보상할 것이 없다 | `404 DELIVERY_MANAGER_NOT_FOUND` |
| 5xx | 배송 삭제로 보상 | `503 SERVICE_UNAVAILABLE` |
| 타임아웃·연결 실패 | 생성됐는지 모르므로 배송 삭제로 보상(orderId 기준 멱등) | `503 SERVICE_UNAVAILABLE` |
| 성공했지만 order의 주문 저장 실패 | 배송 삭제로 보상 | `500 INTERNAL_SERVER_ERROR` |

### 3. "주문 실패"는 이미 전달되고 있다
에러 응답에는 정보가 세 겹으로 들어 있다.

| 정보 | 예 | 알려 주는 것 |
|---|---|---|
| 요청 주소 | `POST /api/v1/orders` (응답의 `path`) | 무엇을 하려다 실패했나: 주문 생성 |
| HTTP 상태 | `404` | 실패의 종류: 대상이 없음 |
| `code`, `message` | `DELIVERY_MANAGER_NOT_FOUND`, "배송담당자가 없습니다" | 왜 실패했나 |

택배 창구에 빗대면 "접수가 안 됩니다(상태). 그 지역 배송 기사가 없어서요(사유)"다. "접수가 안 됩니다"만 들으면 손님은 다시 시도해야 하는지, 다른 지역으로 보내야 하는지 알 수 없다. `code`를 `ORDER_CREATE_FAILED` 하나로 뭉치면 이 사유가 사라진다.

### 4. 그대로 전달하기와 order 코드로 번역하기
| 기준 | 그대로 전달(현재) | order 코드로 번역 |
|---|---|---|
| 클라이언트가 얻는 정보 | 구체적인 사유 | 번역을 얼마나 세밀하게 하느냐에 달렸다. 하나로 뭉치면 사유를 잃는다 |
| 내부 구조 노출 | delivery의 코드 이름이 외부 계약이 된다. delivery가 이름을 바꾸면 클라이언트가 영향을 받는다 | order가 외부 계약을 소유한다. 내부가 바뀌어도 외부는 그대로다 |
| 코드량 | 공통 디코더 하나 | 하위 코드 → order 코드 매핑을 order가 관리한다 |
| SA와 일치 | O-01 에러 목록에 `DELIVERY_MANAGER_NOT_FOUND`가 이미 있다 | SA 변경과 팀 합의가 필요하다 |

이 프로젝트는 클라이언트도 우리가 정의하고 서비스가 다섯 개라서, 그대로 전달해도 노출 비용이 작다. 번역이 필요해지는 때는 외부에 공개하는 API이거나, 하위 서비스를 다른 팀이 소유해 자주 바뀌거나, 하위 코드가 클라이언트에게 의미가 없을 때다.

### 5. 질문의 직관이 맞는 지점: 서버끼리의 문제는 숨겨야 한다
전달할지 숨길지는 "이 에러를 고칠 수 있는 사람이 누구인가"로 나눈다.

| 종류 | 예 | 고칠 사람 | 클라이언트에게 |
|---|---|---|---|
| 비즈니스 사유 | `COMPANY_NOT_FOUND`(없는 업체 ID를 보냄), `DELIVERY_MANAGER_NOT_FOUND` | 클라이언트 또는 운영자 | 그대로 전달 |
| 하위 서비스 장애 | 5xx, 타임아웃 | 서버 운영자 | `503`으로 바꿈(이미 구현) |
| 서버 사이의 계약 오류 | order가 delivery에 필드를 빠뜨려 delivery가 `400 VALIDATION_ERROR`를 줌, 내부 API가 권한을 검사해 `401`/`403`을 줌 | order 개발자 | 숨기고 `500`으로 바꿔야 한다 |

세 번째 줄이 지금 구조의 빈틈이다. `FeignErrorDecoder`는 SA 형식의 4xx를 상태와 관계없이 그대로 담고, `OrderService.createDelivery`도 그대로 다시 던진다. 그래서 order가 delivery에 잘못된 요청을 보내면 클라이언트는 `POST /api/v1/orders`에 대해 `400 VALIDATION_ERROR`를 받는다. 클라이언트는 자기 입력이 틀렸다고 오해하지만, 실제로는 order의 버그다.

고치는 방법은 두 가지가 있다.
- **order에서 허용 목록으로 거른다**: O-01이 클라이언트에 줄 수 있는 하위 코드는 SA에 적힌 `COMPANY_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `DELIVERY_MANAGER_NOT_FOUND`다. 이 목록 밖의 하위 4xx는 로그를 남기고 `500 INTERNAL_SERVER_ERROR`로 바꾼다. order 모듈 안에서 끝나고, O-01 응답이 SA 명세를 벗어나지 않게 보장한다.
- **공통 디코더에서 상태로 거른다**: 404·409 같은 상태만 전달하고 400·401·403은 500으로 바꾼다. 모든 서비스에 한 번에 적용되지만, `MANAGER_HUB_MISMATCH`(400)처럼 전달해야 하는 비즈니스 400도 있어서 상태만으로는 정확히 나눌 수 없다. common 변경이라 팀 전체 리뷰가 필요하다.

어느 쪽이든 숨긴 하위 코드와 orderId는 로그에 남긴다. 응답에서 숨기는 것이지 정보를 버리는 것이 아니다.

## 이 프로젝트에서는
- 틀: `common-core/src/main/java/com/sparta/logistics/common/exception/ErrorCode.java`, `BusinessException.java`, `ErrorResponse.java`, `CommonErrorCode.java`
- 응답 변환: `common/src/main/java/com/sparta/logistics/common/exception/GlobalExceptionHandler.java`
- 하위 에러 변환: `common/src/main/java/com/sparta/logistics/common/feign/FeignErrorDecoder.java`(4xx는 그대로, 5xx와 알 수 없는 형식은 503)
- 주문 Saga와 보상: `order-service/src/main/java/com/sparta/logistics/order/application/OrderService.java`의 `createDelivery`, `compensateDelivery`
- 그대로 전달을 확인하는 테스트: `order-service/src/test/java/com/sparta/logistics/order/integration/OrderHttpIntegrationTest.java`(delivery 404 → 클라이언트 404 `DELIVERY_MANAGER_NOT_FOUND`), `common/src/test/java/com/sparta/logistics/common/feign/FeignErrorDecoderTest.java`
- 규칙: `docs/error-codes.md`, `docs/agents/coding-conventions.md` 2절(에러 코드 이름 규칙), SA 6.1(공통 에러 코드), 6.7 O-01(Error 목록), 7.1(실패 지점별 처리)

## 면접 포인트
- MSA에서 하위 서비스 에러를 어떻게 전파하나: 클라이언트가 대응할 수 있는 비즈니스 에러는 의미를 보존해 전달하고, 하위 장애와 서버 사이 계약 오류는 호출하는 쪽에서 503·500으로 일반화한다. 내부 사정은 응답이 아니라 로그(orderId, traceId)에 남긴다.
- 공통 모듈에 에러 코드를 어디까지 넣나: 틀(인터페이스, 예외, 응답 형식, 핸들러)과 여러 서비스가 같은 뜻으로 쓰는 코드만 넣는다. 도메인 코드를 공통에 넣으면 공통 모듈이 모든 도메인을 알게 되고, 코드 하나 추가에도 전 서비스가 영향을 받는다.
- Saga에서 실패 응답은 어떻게 정하나: 실패한 단계와 남은 데이터에 따라 보상 여부를 정하고, 클라이언트에는 실패 단계에 맞는 에러 코드 하나를 돌려준다. 보상 실패가 최초 실패 원인을 덮지 않게 한다.

## 참고
- 코드 확인: `FeignErrorDecoder.decode`, `OrderService.createDelivery`, `GlobalExceptionHandler.handleBusiness`(2026-10-15, feat/order-create 브랜치)
- SA: `docs/SA/sparta-logistics-sa.md` 6.1, 6.7 O-01, 7.1
