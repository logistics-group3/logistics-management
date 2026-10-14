package com.sparta.logistics.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.logistics.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 에러 응답: { status, code, message, path, timestamp, errors }.
 * Gateway, 각 서비스, Feign ErrorDecoder가 모두 같은 형식을 쓴다. errors는 VALIDATION_ERROR일 때만 채운다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        String message,
        String path,
        LocalDateTime timestamp,
        List<FieldError> errors
) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), message, path,
                LocalDateTime.now(), null);
    }

    public static ErrorResponse validation(String message, String path, List<FieldError> errors) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new ErrorResponse(code.getStatus().value(), code.name(), message, path,
                LocalDateTime.now(), errors);
    }
}
