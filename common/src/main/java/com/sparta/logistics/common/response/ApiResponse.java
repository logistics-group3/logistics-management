package com.sparta.logistics.common.response;

import org.springframework.http.HttpStatus;

/**
 * 외부 API(/api/v1/**) 성공 응답 형식: {status, message, data}.
 * 내부 API(/internal/**)는 이 래퍼 없이 DTO를 그대로 반환한다.
 */
public record ApiResponse<T>(int status, String message, T data) {

    private static final String DEFAULT_MESSAGE = "요청이 성공했습니다.";

    public static <T> ApiResponse<T> ok(T data) {
        return of(HttpStatus.OK, DEFAULT_MESSAGE, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    public static ApiResponse<Void> ok() {
        return of(HttpStatus.OK, DEFAULT_MESSAGE, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return of(HttpStatus.CREATED, "생성되었습니다.", data);
    }

    public static <T> ApiResponse<T> of(HttpStatus status, String message, T data) {
        return new ApiResponse<>(status.value(), message, data);
    }
}
