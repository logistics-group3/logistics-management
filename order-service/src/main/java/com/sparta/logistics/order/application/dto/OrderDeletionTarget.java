package com.sparta.logistics.order.application.dto;

import java.util.UUID;

public record OrderDeletionTarget(UUID orderId, UUID deliveryId, boolean deliveryCheckRequired) {
}
