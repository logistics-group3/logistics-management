# 스텝 3. common 응답·예외

- 날짜: 2026-10-13
- 결과: SA 6.1의 성공·목록·에러 응답 형식을 common에 구현. 단위 테스트 5개 통과(`./gradlew :common:test`)

## 만든 것 (`common/src/main/java/com/sparta/logistics/common/`)
| 파일 | 역할 |
|---|---|
| `response/ApiResponse` | `status`, `message`, `data`. `ok(data)`, `ok(message, data)`, `created(message, data)` |
| `response/PageResponse` | `content`, `page`, `size`, `totalElements`, `totalPages`, `sort`. `PageResponse.from(page)` 한 줄로 변환 |
| `exception/ErrorCode` | 인터페이스: `status()`, `code()`, `message()` |
| `exception/CommonErrorCode` | 공통 코드: VALIDATION_ERROR, INVALID_REQUEST, UNAUTHORIZED, TOKEN_ROLE_MISMATCH, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED, SERVICE_UNAVAILABLE, INTERNAL_SERVER_ERROR |
| `exception/BusinessException` | ErrorCode를 담아 던지는 예외 |
| `exception/ErrorResponse` | `status`, `code`, `message`, `path`, `timestamp`, `errors`(VALIDATION_ERROR일 때만) |
| `exception/GlobalExceptionHandler` | 모든 예외를 ErrorResponse로 변환 |

## 리뷰 포인트
1. **ErrorCode는 인터페이스**: common에는 공통 코드만 두고, 서비스는 `UserErrorCode implements ErrorCode` 같은 자기 enum을 만든다. common이 서비스 도메인을 몰라도 되고, 모든 코드가 같은 처리 흐름을 탄다. `code()`는 enum 상수 이름을 그대로 쓰므로 "대문자 스네이크" 규칙이 자동으로 지켜진다.
2. **서비스에서 쓰는 법**
   ```java
   throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
   return ApiResponse.created("회원가입이 완료되었습니다.", response);
   return ApiResponse.ok(PageResponse.from(page));
   ```
3. **예외 분류**
   - 본문 검증 실패(`@Valid`): VALIDATION_ERROR + 필드별 `errors`
   - JSON 파싱 실패, Enum에 없는 값, 타입 불일치, 필수 파라미터·헤더 누락: VALIDATION_ERROR (SA 8.5 "Enum 바인딩 실패 시 400")
   - 없는 경로: NOT_FOUND, 메서드 불일치: METHOD_NOT_ALLOWED
   - 그 외: INTERNAL_SERVER_ERROR. 원래 메시지(예: DB 정보)는 응답에 노출하지 않고 ERROR 로그로만 남긴다(테스트로 확인).
4. **timestamp**: 초 단위로 잘라 `2026-10-13T10:15:30` 형식으로 내보낸다. 컨테이너 기본 시간대가 UTC라서 Dockerfile에 `TZ=Asia/Seoul`을 추가했다.
5. **테스트 방식**: 스프링 컨텍스트를 띄우지 않고 `MockMvcBuilders.standaloneSetup(컨트롤러).setControllerAdvice(핸들러)`로 빠르게 검증한다. common에는 실행 가능한 앱이 없어서 이 방식이 맞다.

## 면접 포인트
- `@RestControllerAdvice`의 동작 범위와, 필터에서 난 예외는 여기서 잡히지 않는 이유(DispatcherServlet 바깥)
- 에러 응답에 내부 예외 메시지를 그대로 내보내면 안 되는 이유
