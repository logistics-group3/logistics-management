package com.sparta.logistics.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드. 서비스마다 enum으로 구현한다 (예: {@code UserErrorCode implements ErrorCode}).
 * 응답의 {@code code}는 enum 상수 이름 그대로 쓴다 (SA 6.1: {@code USER_NOT_FOUND}, {@code STOCK_INSUFFICIENT} ...).
 *
 * <pre>
 * &#64;Getter
 * &#64;RequiredArgsConstructor
 * public enum HubErrorCode implements ErrorCode {
 *     HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
 *     STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "허브 재고가 부족합니다.");
 *
 *     private final HttpStatus status;
 *     private final String message;
 * }
 * </pre>
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /** enum이면 자동으로 구현된다. */
    String name();

    default String getCode() {
        return name();
    }
}
