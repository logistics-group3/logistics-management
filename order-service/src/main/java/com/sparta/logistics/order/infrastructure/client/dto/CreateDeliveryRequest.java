package com.sparta.logistics.order.infrastructure.client.dto;

import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import java.util.UUID;

/** I-09의 JSON 필드 계약(SA 6.3). */
public record CreateDeliveryRequest(UUID orderId, UUID supplierCompanyId, UUID receiverCompanyId,
                                    UUID departureHubId, UUID destinationHubId, String pickupAddress,
                                    String deliveryAddress, String recipientName, UUID receiverManagerId) {

    public static CreateDeliveryRequest from(CreateDeliveryCommand command) {
        return new CreateDeliveryRequest(command.orderId(), command.supplierCompanyId(), command.receiverCompanyId(),
                command.departureHubId(), command.destinationHubId(), command.pickupAddress(), command.deliveryAddress(),
                command.recipientName(), command.receiverManagerId());
    }
}
