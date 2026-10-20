package com.sparta.logistics.order.application.dto;

import java.util.UUID;

public record DeliveryCreationState(UUID orderId, String state, UUID deliveryId, String status) {
}
