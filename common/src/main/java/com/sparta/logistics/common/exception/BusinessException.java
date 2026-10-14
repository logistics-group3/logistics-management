package com.sparta.logistics.common.exception;

import lombok.Getter;

/** 비즈니스 규칙 위반. Feign ErrorDecoder도 하위 서비스의 에러를 이 예외로 바꾼다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
