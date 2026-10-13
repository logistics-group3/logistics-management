package com.sparta.logistics.common.exception;

import lombok.Getter;

/**
 * 비즈니스 예외. 서비스 코드에서는 이 예외만 던지면 GlobalExceptionHandler 가 SA 에러 형식으로 변환한다.
 * <pre>throw new BusinessException(UserErrorCode.USER_NOT_FOUND);</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황에 맞는 메시지를 쓰고 싶을 때 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
