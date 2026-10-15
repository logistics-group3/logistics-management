package com.sparta.logistics.delivery.presentation.dto;

import com.sparta.logistics.delivery.domain.DeliverySearchCondition;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.util.UUID;

/** D-01 검색 파라미터(SA 6.8). 모두 선택이다. */
public record DeliverySearchRequest(
        DeliveryStatus status,
        UUID departureHubId,
        UUID destinationHubId,
        UUID managerId,
        UUID orderId,
        UUID supplierCompanyId,
        UUID receiverCompanyId
) {

    public DeliverySearchCondition toCondition() {
        return new DeliverySearchCondition(status, departureHubId, destinationHubId, managerId, orderId,
                supplierCompanyId, receiverCompanyId, null);
    }
}
