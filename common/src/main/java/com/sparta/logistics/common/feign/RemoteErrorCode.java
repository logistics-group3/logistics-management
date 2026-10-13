package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 하위 서비스가 돌려준 에러 코드를 그대로 상위로 전달하기 위한 ErrorCode 구현 (예: hub 의 STOCK_INSUFFICIENT) */
public class RemoteErrorCode implements ErrorCode {

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
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
