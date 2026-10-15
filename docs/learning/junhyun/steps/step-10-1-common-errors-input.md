# 스텝 10-1. common 예외·입력 규칙 보강 (리뷰 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 1](review-01-common-infra.md) major 2, minor 6·7, nit(빈 정렬 문자열)
- 결과: common 테스트 21개 통과(이전 13개), 전체 빌드 통과

## 무엇을 고쳤나
| 문제 | 수정 |
|---|---|
| Spring MVC 표준 4xx(415, 406, 405, 404)가 마지막 `Exception` 처리기로 흘러 500이 됨. `text/plain` 가입 요청이 500으로 재현됨 | `ServletException`, `ErrorResponseException` 처리기를 추가했다. Spring 표준 예외는 `org.springframework.web.ErrorResponse`를 구현하고 올바른 상태를 갖고 있으므로, 그 상태를 공통 코드로 바꾼다(415 → UNSUPPORTED_MEDIA_TYPE, 406 → NOT_ACCEPTABLE 추가) |
| 컨트롤러 파라미터 검증(`@RequestParam @Min(1)`) 실패가 500 | `HandlerMethodValidationException` 처리기 추가. 파라미터 이름별 `errors`를 담아 400 VALIDATION_ERROR |
| 정렬 파라미터에 없는 필드(`sort=password,DESC`)를 넣으면 500 | `PropertyReferenceException` → 400 VALIDATION_ERROR, field `sort` |
| `"affiliationType": 1`이 HUB로 가입됨(Jackson이 숫자를 Enum 선언 순서로 변환) | `JacksonConfig`: `FAIL_ON_NUMBERS_FOR_ENUMS` 활성화. 문자열만 받는다 |
| SA 6.1 검색 규칙(기본 10건, createdAt DESC, size 10/30/50 외에는 10)을 각 서비스가 따로 구현해야 함 | `SearchPageableArgumentResolver`: 모든 서비스의 `Pageable` 파라미터에 자동 적용 |
| 정렬이 없을 때 `PageResponse.sort`가 빈 문자열 | `null`로 바꿔 응답에서 비어 있음을 분명히 함 |
| `ErrorCode.status()`가 `HttpStatus` enum이라 임의의 상태를 담을 수 없음 | `HttpStatusCode`로 넓히고, 동적 코드용 `SimpleErrorCode` record 추가(10-2 Feign 오류 전달에도 사용) |

## 고치다 발견한 것
- **`-parameters` 컴파일 옵션 누락**: 파라미터 검증 테스트가 400이 아니라 500으로 실패했다. 원인은 "Name for argument of type [int] not specified" 예외였다. Spring은 `@RequestParam`의 이름을 리플렉션으로 읽는데, 그러려면 `-parameters` 컴파일 옵션이 필요하다. Boot 플러그인을 쓰는 서비스 모듈에는 자동으로 붙지만, 라이브러리 모듈인 common에는 빠져 있었다. 루트 `subprojects`에서 모든 모듈에 명시했다.
- **Pageable 처리기 순서**: Spring Data도 `WebMvcConfigurer`로 Pageable 처리기를 등록한다. 처리기 목록은 먼저 등록된 것이 이긴다. 그래서 common의 `WebMvcConfig`에 `@Order(HIGHEST_PRECEDENCE)`를 붙여 공통 규칙이 먼저 등록되게 했다.

## 서비스에서 쓰는 법
```java
@GetMapping
public ApiResponse<PageResponse<HubResponse>> search(HubSearchRequest condition, Pageable pageable) {
    // pageable은 이미 SA 6.1 규칙으로 보정되어 있다. 정렬 허용 필드는 API별로 직접 검증한다.
    return ApiResponse.ok(PageResponse.from(hubService.search(condition, pageable)));
}
```

## 면접 포인트
- `@ExceptionHandler`가 여러 개 맞을 때 고르는 기준(가장 가까운 예외 타입)과, 마지막 `Exception` 처리기가 표준 예외를 삼키는 문제
- Enum을 숫자(ordinal)로 저장하거나 받으면 안 되는 이유
