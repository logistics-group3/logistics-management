package com.sparta.logistics.gateway.support;

import org.springframework.http.HttpStatus;

/** Gateway가 직접 응답하는 에러. 코드와 문구는 common의 CommonErrorCode와 같다. */
public enum GatewayErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_ROLE_MISMATCH(HttpStatus.UNAUTHORIZED, "권한 정보가 변경되었습니다. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    GatewayErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
