package com.sparta.logistics.delivery.infrastructure.client.dto;

import java.util.UUID;

/** user I-14 응답 원소(SA 6.3). */
public record DeliveryManagerResponse(UUID userId, String slackId) {
}
