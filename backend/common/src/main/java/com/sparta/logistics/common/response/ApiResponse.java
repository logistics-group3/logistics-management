package com.sparta.logistics.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 외부 API 성공 응답 (SA 6.1).
 * <pre>
 * { "status": 200, "message": "SUCCESS", "data": { ... } }
 * </pre>
 * 컨트롤러에서: {@code return ApiResponse.ok(OrderResponse.from(result));}
 * <br>내부 API({@code /internal/**})는 래퍼 없이 DTO를 그대로 반환한다 (Feign 클라이언트 단순화).
 */
public record ApiResponse<T>(int status, String message, T data) {

    public static final String DEFAULT_MESSAGE = "SUCCESS";

    /** 200 + "SUCCESS" */
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return of(HttpStatus.OK, DEFAULT_MESSAGE, data);
    }

    /** 200 + 지정 메시지 (예: "로그인에 성공했습니다.") */
    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    /** 201 + 지정 메시지 (예: "주문이 생성되었습니다.") */
    public static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        return of(HttpStatus.CREATED, message, data);
    }

    public static <T> ResponseEntity<ApiResponse<T>> of(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(new ApiResponse<>(status.value(), message, data));
    }
}
