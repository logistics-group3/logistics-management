package com.sparta.logistics.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 계약. 공통 코드는 {@link CommonErrorCode}, 도메인 코드(USER_NOT_FOUND, STOCK_INSUFFICIENT 등)는
 * 각 서비스가 이 인터페이스를 구현한 enum으로 정의한다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
