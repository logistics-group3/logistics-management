package com.sparta.logistics.common.exception;

import lombok.Getter;

/**
 * 서비스에서 던지는 예외는 이것 하나. {@code RuntimeException}, {@code IllegalArgumentException}을 직접 던지지 않는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황에 맞는 메시지를 쓸 때 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /** Feign으로 받은 원격 에러와 비교할 때 사용 (예: {@code e.hasCode(HubErrorCode.STOCK_INSUFFICIENT)}) */
    public boolean hasCode(ErrorCode other) {
        return errorCode.getCode().equals(other.getCode());
    }
}
