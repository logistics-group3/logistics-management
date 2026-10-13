package com.sparta.logistics.common.response;

import org.springframework.http.HttpStatus;

/**
 * 성공 응답 (SA 6.1)
 * <pre>{ "status": 200, "message": "SUCCESS", "data": { } }</pre>
 */
public record ApiResponse<T>(int status, String message, T data) {

    private static final String DEFAULT_MESSAGE = "SUCCESS";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), DEFAULT_MESSAGE, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), message, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(HttpStatus.OK.value(), DEFAULT_MESSAGE, null);
    }

    /** 컨트롤러에 @ResponseStatus(HttpStatus.CREATED) 를 함께 붙여 HTTP 상태 코드도 201 로 맞춘다. */
    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), message, data);
    }
}
