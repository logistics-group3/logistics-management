package com.sparta.logistics.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 성공 응답: { "status": 200, "message": "SUCCESS", "data": { } } */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int status, String message, T data) {

    private static final String SUCCESS = "SUCCESS";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, SUCCESS, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(200, SUCCESS, null);
    }

    /** 201 응답용. 컨트롤러에 {@code @ResponseStatus(HttpStatus.CREATED)}를 함께 선언한다. */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, SUCCESS, data);
    }
}
