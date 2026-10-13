package com.sparta.logistics.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.logistics.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 에러 응답 (SA 6.1)
 * <pre>
 * { "status": 400, "code": "VALIDATION_ERROR", "message": "...", "path": "/api/v1/auth/signup",
 *   "timestamp": "2026-10-13T10:15:30", "errors": [ { "field": "username", "reason": "..." } ] }
 * </pre>
 * errors 는 VALIDATION_ERROR 일 때만 포함한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        String message,
        String path,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,
        List<FieldError> errors
) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.getMessage(), path);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), message, path,
                LocalDateTime.now(), null);
    }

    public static ErrorResponse ofValidation(ErrorCode errorCode, String path, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), errorCode.getMessage(), path,
                LocalDateTime.now(), errors);
    }
}
