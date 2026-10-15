package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.infrastructure.client.dto.CreateDeliveryRequest;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryClientAdapter implements DeliveryGateway {

    private final DeliveryCreateClient createClient;
    private final DeliveryDeleteClient deleteClient;

    @Override
    public UUID create(CreateDeliveryCommand command) {
        DeliveryResponse response = createClient.create(CreateDeliveryRequest.from(command));
        if (response == null || response.deliveryId() == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "배송 생성 응답에 ID가 없습니다.");
        }
        return response.deliveryId();
    }

    @Override
    public void deleteByOrderId(UUID orderId) {
        deleteClient.deleteByOrderId(orderId);
    }
}
