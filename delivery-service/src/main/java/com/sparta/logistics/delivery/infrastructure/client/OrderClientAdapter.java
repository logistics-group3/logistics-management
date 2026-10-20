package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.application.assignment.OrderCreationCheck;
import com.sparta.logistics.delivery.application.assignment.OrderCreationReader;
import com.sparta.logistics.delivery.infrastructure.client.dto.OrderCreationResultResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** order의 주문 생성 결과(I-21)를 배송 쪽 판단 값으로 옮긴다. */
@Component
@RequiredArgsConstructor
public class OrderClientAdapter implements OrderCreationReader {

    private final OrderClient orderClient;

    @Override
    public OrderCreationCheck getCreationResult(UUID orderId) {
        OrderCreationResultResponse response = orderClient.getCreationResult(orderId);
        if (response == null || response.result() == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "주문 생성 결과를 확인할 수 없습니다.");
        }
        return new OrderCreationCheck(response.result(), response.orderStatus());
    }
}
