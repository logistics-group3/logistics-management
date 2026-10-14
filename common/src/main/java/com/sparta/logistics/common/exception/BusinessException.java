package com.sparta.logistics.common.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 예외. {@link GlobalExceptionHandler}가 ErrorCode의 HTTP 상태와 코드로 응답한다.
 */
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
