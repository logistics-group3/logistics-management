package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.infrastructure.client.dto.CreateDeliveryRequest;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryResponse;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryStatusResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryClientAdapter implements DeliveryGateway {

    private final DeliveryCreateClient createClient;
    private final DeliveryDeleteClient deleteClient;
    private final DeliveryCancelClient cancelClient;
    private final DeliveryCancelRevertClient cancelRevertClient;

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
        ResponseEntity<Void> response = deleteClient.deleteByOrderId(orderId);
        if (response == null || response.getStatusCode() != HttpStatus.NO_CONTENT) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "배송 삭제 완료 응답을 확인할 수 없습니다.");
        }
    }

    @Override
    public void cancel(UUID deliveryId) {
        DeliveryStatusResponse response = cancelClient.cancel(deliveryId);
        if (response == null || !"CANCELED".equals(response.status())) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "배송 취소 결과를 확인할 수 없습니다.");
        }
    }

    @Override
    public void revertCancellation(UUID deliveryId) {
        cancelRevertClient.revert(deliveryId);
    }
}
