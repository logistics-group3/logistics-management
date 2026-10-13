package com.sparta.logistics.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 규약. 코드명은 대문자 스네이크 케이스 (예: USER_NOT_FOUND).
 *
 * 공통 코드는 {@link CommonErrorCode}, 도메인 코드는 각 서비스에서 이 인터페이스를 구현한 enum 으로 만든다.
 * <pre>
 * public enum UserErrorCode implements ErrorCode {
 *     USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
 *     ...
 *     public String getCode() { return name(); }
 * }
 * </pre>
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
