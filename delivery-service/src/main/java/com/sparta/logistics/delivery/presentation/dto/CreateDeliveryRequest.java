package com.sparta.logistics.delivery.presentation.dto;

import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** I-09 요청(SA 6.3). 길이는 p_deliveries 컬럼과 같다. */
public record CreateDeliveryRequest(
        @NotNull UUID orderId,
        @NotNull UUID supplierCompanyId,
        @NotNull UUID receiverCompanyId,
        @NotNull UUID departureHubId,
        @NotNull UUID destinationHubId,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotBlank @Size(max = 100) String recipientName,
        @NotNull UUID receiverManagerId
) {

    public CreateDeliveryCommand toCommand() {
        return new CreateDeliveryCommand(orderId, supplierCompanyId, receiverCompanyId, departureHubId, destinationHubId,
                pickupAddress, deliveryAddress, recipientName, receiverManagerId);
    }
}
