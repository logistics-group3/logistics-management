# 0006. 웹 무관 공통 계약을 common-core 모듈로 분리

- 상태: 채택
- 날짜: 2026-10-14
- 결정자: junhyun (공통 인프라 개인 구현안)

## 맥락
- common은 Spring MVC 기반(spring-boot-starter-web)이라 WebFlux 기반 gateway가 의존할 수 없다.
- 그래서 gateway에 사용자 헤더 이름(UserHeaders), 에러 코드와 문구(GatewayErrorCode), I-01 응답 봉투(ApiEnvelope)를 따로 정의했다. 한쪽만 고치면 서비스와 gateway의 응답이 어긋날 수 있다(리뷰 2 A-4).

## 결정
- 새 모듈 `common-core`(java-library)에 웹 스택과 무관한 계약만 둔다: ErrorCode, CommonErrorCode, SimpleErrorCode, BusinessException, ErrorResponse, ApiResponse, UserHeaders, Role
- 의존성은 spring-web(HttpStatus 등, MVC와 WebFlux 공통 기반)과 jackson-annotations만 둔다.
- 패키지 이름(`com.sparta.logistics.common.*`)은 그대로 둔다. common은 `api project(':common-core')`로 노출한다. 그래서 서비스 코드와 import는 바뀌지 않는다.
- gateway는 common-core만 의존하고, 중복 정의를 지운다.

## 검토한 대안
- common을 `common-core`와 `common-web`으로 이름까지 나누기: 서비스 5개의 의존성 선언과 문서를 모두 바꿔야 한다. 이름을 유지해도 목적은 같다.
- 중복을 두고 동일성 테스트로 감시: 어긋남은 잡지만 중복 자체는 남는다.

## 결과
- 응답 형식은 `ErrorResponseContractTest`(common-core) 하나로 서비스와 gateway에 함께 고정된다.
- common-core에 웹 스택 의존(MVC, WebFlux)을 넣지 않는 규칙을 지켜야 한다.
