package com.sparta.logistics.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 다른 서비스가 돌려준 4xx 에러를 의미 그대로 전달할 때 사용한다 ({@link com.sparta.logistics.common.feign.FeignErrorDecoder}).
 * 예: hub-service 409 STOCK_INSUFFICIENT → order-service도 409 STOCK_INSUFFICIENT
 */
public final class RemoteErrorCode implements ErrorCode {

    private final HttpStatus status;
    private final String code;
    private final String message;

    public RemoteErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String name() {
        return code;
    }
}
