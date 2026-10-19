package com.sparta.logistics.delivery.domain;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * delivery-service 에러 코드(SA 6.1, 6.8). 목록은 docs/error-codes.md.
 * DELIVERY_MANAGER_NOT_FOUND, MANAGER_HUB_MISMATCH는 SA 6.1 공통 표에 있지만 배송만 만드는 코드라 여기에 둔다.
 * order는 I-09 응답 코드를 FeignErrorDecoder로 그대로 전달하므로 이름을 바꾸지 않는다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode {

    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."),
    DELIVERY_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "배송 구간을 찾을 수 없습니다."),
    DELIVERY_MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "배정할 배송담당자가 없습니다."),
    MANAGER_HUB_MISMATCH(HttpStatus.BAD_REQUEST, "배송담당자의 소속 허브가 구간 담당 허브와 다릅니다."),
    DELIVERY_CREATION_CLOSED(HttpStatus.CONFLICT, "이미 종료된 배송 생성 시도입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String code() {
        return name();
    }
}
