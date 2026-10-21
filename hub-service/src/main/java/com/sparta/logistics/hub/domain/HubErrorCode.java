package com.sparta.logistics.hub.domain;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum HubErrorCode implements ErrorCode {

    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
    HUB_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "허브 간 경로를 찾을 수 없습니다."),
    STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "재고가 부족합니다."),
    STOCK_ALREADY_RESTORED(HttpStatus.CONFLICT, "이미 복원한 주문은 다시 할당할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String code() {
        return name();
    }
}
