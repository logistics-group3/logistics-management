---
title: BusinessException을 상속한 도메인 예외를 따로 만드는 것이 나은가, ErrorCode만 갈아 끼우는 것이 나은가
date: 2026-10-15
updated: 2026-10-15
asker: choiseungkeun
level: 중급
tags: [spring, exception, error-code, msa]
---

# BusinessException을 상속한 도메인 예외를 따로 만드는 것이 나은가, ErrorCode만 갈아 끼우는 것이 나은가

## 질문
공통 예외 처리 모듈을 만들었고, `BusinessException`을 상속하는 도메인용 Exception을 만든다고 들었다. 그 방법을 어떻게 생각하는가.

## 짧은 답
- 이 프로젝트에서는 `BusinessException(서비스ErrorCode.XXX)`를 그대로 던지는 방식을 권한다. 도메인 예외 클래스를 건별로 만들지 않는다.
- 응답 변환이 `ErrorCode`의 상태와 코드로 이루어지므로 서브클래스가 응답에 더하는 정보가 없다.
- 서비스 간에는 예외 타입이 아니라 `code` 문자열이 전달되므로, 타입으로 분기하는 방식은 서비스 경계를 넘지 못한다.
- 서브클래스는 타입별로 다르게 처리하거나 추가 데이터를 실어야 할 때 만든다.

## 자세히

### 서비스 단위 베이스 예외(`HubException extends BusinessException`, `ErrorCode`를 받음)
- 에러마다 클래스를 만드는 방식과 달리 서비스당 하나만 둔다. 에러가 늘어도 enum 상수만 추가하면 된다.
- 생성자가 `ErrorCode`를 받으면 `HubErrorCode`와 `CommonErrorCode`를 모두 던질 수 있다. 부모와 받는 값이 같아 타입 제약 이점은 없다.
- 남는 이득은 "이 예외가 hub에서 났다"는 타입 표시다. 서비스 단위로 로그·메트릭을 나누거나 핸들러를 둘 근거가 된다.
- 한계: 응답 변환에는 영향이 없다. Feign으로 복원된 예외는 `BusinessException`이라 `HubException`으로는 잡히지 않는다.
- 생성자를 `HubErrorCode`로 한정하면 다른 서비스 코드를 던지는 실수를 컴파일 단계에서 막지만, `CommonErrorCode`를 던지는 도메인 규칙과 충돌한다.
- 판단: 해가 없고 이득도 작은 선택이다. 팀 일관성을 위해 도입할 수는 있으나, 기능 때문에 도입할 이유는 약하다.

### ErrorCode 방식(`new BusinessException(HubErrorCode.HUB_NOT_FOUND)`)
- 에러 하나를 추가하는 비용이 enum 상수 한 줄이다. `docs/error-codes.md`와 1:1로 대응한다.
- `GlobalExceptionHandler`는 `BusinessException` 하나만 처리하면 된다.
- 던지는 곳이 길어지는 단점은 정적 팩토리나 `orElseThrow` 람다로 줄일 수 있다.

### 서브클래스가 정당한 경우
- 예외마다 응답을 다르게 만들어야 할 때. 예: `common`의 `InvalidSortException`은 `RuntimeException`을 상속하고 별도 핸들러가 있다.
- 예외가 호출자에게 구조화된 데이터를 넘겨야 할 때.
- 같은 던지기가 여러 곳에서 반복돼 한 곳에 모으고 싶을 때.

## 이 프로젝트에서는
- `common-core`의 `BusinessException(ErrorCode)`와 `GlobalExceptionHandler.handleBusiness`가 이미 있고, user-service는 `new BusinessException(UserErrorCode.USER_NOT_FOUND)`를 쓴다(`UserQueryService`, `AuthService`).
- 하위 서비스의 4xx는 `FeignErrorDecoder`가 응답의 `code`를 `SimpleErrorCode`에 담아 `BusinessException`으로 복원한다. 호출한 쪽은 타입이 아니라 `code`로 분기하므로 서브클래스의 이점이 서비스 경계에서 사라진다.
- 코딩 규칙(`docs/agents/coding-conventions.md` 2절)은 "ErrorCode 기반 커스텀 예외"로 던지라고만 한다. 서브클래스를 도입하면 팀 규칙이 바뀌는 것이므로 공통 모듈 담당자와 합의 후 ADR로 남긴다.
- hub-service는 `HubErrorCode`(`HUB_NOT_FOUND`, `HUB_ROUTE_NOT_FOUND`)와 `BusinessException` 조합을 쓴다.

## 면접 포인트
- 질문: 예외 클래스를 에러마다 만드는 것과 에러 코드 enum을 쓰는 것의 차이는?
  답 요지: 클래스는 타입으로 분기할 수 있지만 개수가 늘고 응답에 정보를 더하지 않는다. enum은 코드·상태·메시지를 한 곳에 모아 응답과 문서가 일치한다. 분기 대상이 서비스 경계를 넘는 MSA에서는 문자열 코드가 계약이다.
- 질문: 서비스 간 호출에서 예외는 어떻게 전달되는가?
  답 요지: 예외 객체가 아니라 HTTP 응답 본문의 코드로 전달되고, 호출한 쪽이 디코더에서 예외로 복원한다.

## 참고
- `common-core/src/main/java/com/sparta/logistics/common/exception/BusinessException.java`
- `common/src/main/java/com/sparta/logistics/common/feign/FeignErrorDecoder.java`
- `docs/agents/coding-conventions.md` 2절, `docs/error-codes.md`
