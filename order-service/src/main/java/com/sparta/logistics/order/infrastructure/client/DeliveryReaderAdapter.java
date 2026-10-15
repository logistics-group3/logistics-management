package com.sparta.logistics.order.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.DeliveryReader;
import com.sparta.logistics.order.infrastructure.client.dto.DeliveryLookupResponse;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryReaderAdapter implements DeliveryReader {
    private static final Set<String> STATUSES = Set.of("PICKUP_WAITING", "PICKING_UP", "HUB_WAITING", "HUB_MOVING",
            "DEST_HUB_ARRIVED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELED");
    private final DeliveryQueryClient client;

    @Override
    public boolean isDelivered(UUID orderId, UUID deliveryId) {
        DeliveryLookupResponse response = client.getDelivery(deliveryId);
        if (response == null || !deliveryId.equals(response.deliveryId()) || !orderId.equals(response.orderId())
                || response.status() == null || !STATUSES.contains(response.status())) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "주문에 연결된 배송 상태를 확인할 수 없습니다.");
        }
        return "DELIVERED".equals(response.status());
    }
}
