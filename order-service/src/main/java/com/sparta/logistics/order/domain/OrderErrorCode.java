package com.sparta.logistics.order.domain;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 멱등키에 다른 주문 요청을 사용할 수 없습니다."),
    ORDER_CREATION_IN_PROGRESS(HttpStatus.CONFLICT, "같은 키의 주문 생성이 진행 중입니다."),
    ORDER_CREATION_ABORTED(HttpStatus.CONFLICT, "같은 키의 주문 생성은 실패로 종료되었습니다. 새 주문에는 새 키를 사용하세요."),
    ORDER_CREATION_RECOVERY_REQUIRED(HttpStatus.CONFLICT, "기존 주문 생성 결과의 확인이 필요합니다. 새 주문으로 재실행하지 마세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String code() {
        return name();
    }
}
