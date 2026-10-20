package com.sparta.logistics.hub.application.dto;

import java.util.UUID;

public record InitializeStockCommand(UUID hubId, UUID productId) {
}
