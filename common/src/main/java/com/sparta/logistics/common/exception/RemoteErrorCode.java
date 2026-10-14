package com.sparta.logistics.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 하위 서비스가 돌려준 에러(4xx)를 그대로 전달하기 위한 ErrorCode.
 * 예: hub-service의 409 STOCK_INSUFFICIENT를 order-service 응답으로 그대로 내보낸다.
 */
@Getter
@RequiredArgsConstructor
public class RemoteErrorCode implements ErrorCode {

    private final HttpStatus status;
    private final String code;
    private final String message;
}
