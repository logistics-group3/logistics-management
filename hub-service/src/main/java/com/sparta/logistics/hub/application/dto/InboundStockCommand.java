package com.sparta.logistics.hub.application.dto;

import java.util.UUID;

public record InboundStockCommand(UUID hubId, UUID productId, int quantity) {
}
