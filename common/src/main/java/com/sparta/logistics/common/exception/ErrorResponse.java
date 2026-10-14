package com.sparta.logistics.common.exception;

import java.time.LocalDateTime;

/**
 * 에러 응답 형식: {status, code, message, path, timestamp}. 외부·내부 API 모두 동일하다.
 */
public record ErrorResponse(int status, String code, String message, String path, LocalDateTime timestamp) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), message, path, LocalDateTime.now());
    }
}
