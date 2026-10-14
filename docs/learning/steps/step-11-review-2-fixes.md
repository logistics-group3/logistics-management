# 스텝 11. 직접 구현·하드코딩 정리 (리뷰 2 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 2](review-02-reinvention-check.md) A-1~A-4
- 결과: 테스트 63개 통과(common-core 2 추가), 빈 상태에서 e2e PASS 17/0(135초). Gateway 경유 405 응답에 `Allow: POST` 헤더 확인

## 11-1. 공통 예외 처리기를 ResponseEntityExceptionHandler 상속으로
- 전: Spring MVC 표준 예외를 `ServletException` 처리기와 상태 코드 switch로 직접 매핑했다(스텝 10-1).
- 후: `ResponseEntityExceptionHandler`를 상속한다. Spring이 표준 예외 약 20종의 상태와 헤더를 정하고, 마지막에 `handleExceptionInternal`을 부른다. 그 메서드만 오버라이드해 본문을 SA 6.1 형식으로 바꾼다. 필드별 사유가 필요한 `handleMethodArgumentNotValid`, `handleHandlerMethodValidationException`만 따로 오버라이드한다.
- 덤으로 얻은 것: 405 응답의 `Allow`, 415 응답의 `Accept` 같은 HTTP 표준 헤더가 유지된다. 직접 매핑할 때는 빠져 있었다. 테스트로 `Allow: POST`를 확인한다.
- 교훈: 내가 만든 `ServletException` 처리기는 "표준 예외는 대부분 ServletException"이라는 가정에 기대고 있었다. 프레임워크가 이미 그 목록을 관리하고 있으면 그것을 쓰는 편이 빠뜨림이 없다.

## 11-2. 실행 jar 이름 고정
- 전: Dockerfile이 `${MODULE}-0.0.1-SNAPSHOT.jar`를 복사했다. 버전을 올리면 이미지 빌드가 깨진다.
- 후: 루트 `build.gradle`에서 Boot 플러그인을 쓰는 모듈의 `bootJar` 산출물 이름을 `app.jar`로 고정하고, 실행할 수 없는 plain jar는 만들지 않는다. Dockerfile은 `build/libs/app.jar`만 복사한다.
- `plugins.withId('org.springframework.boot') { ... }`: 그 플러그인이 적용된 모듈에만 설정을 건다. common 같은 라이브러리 모듈의 jar는 그대로 둔다.

## 11-3. common-core 분리 ([ADR 0006](../../decisions/0006-common-core-module.md))
- 웹 스택과 무관한 계약(에러 코드, ErrorResponse, ApiResponse, UserHeaders, Role, BusinessException)을 `common-core`로 옮겼다. 패키지는 그대로라 서비스 코드는 한 줄도 바뀌지 않았다.
- gateway는 common-core를 써서 `UserHeaders`, `GatewayErrorCode`, `ApiEnvelope` 중복을 지웠다. Gateway의 에러 응답도 서비스와 같은 `ErrorResponse` 객체로 만든다.
- `ErrorResponseContractTest`가 에러 응답 모양(errors는 VALIDATION_ERROR일 때만)을 한곳에서 고정한다.

## 11-4. gateway 생성자 주입 통일
- 필드를 대입하기만 하던 생성자 6곳을 `@RequiredArgsConstructor`로 바꿨다. 코딩 규칙과 다른 모듈의 스타일에 맞췄다.
- `CachedUserLookup`은 테스트용 Ticker 생성자가 따로 있어서 그대로 둔다.

## 남긴 것
- 리뷰 2 A-5(user-service `SecurityConfig` 이름)는 이번 요청 범위 밖이라 그대로 뒀다.
- JaCoCo 도입은 미루기로 했다.
