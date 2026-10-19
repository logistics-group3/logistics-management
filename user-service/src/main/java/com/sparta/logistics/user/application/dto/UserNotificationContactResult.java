package com.sparta.logistics.user.application.dto;

import java.util.UUID;

public record UserNotificationContactResult(UUID userId, boolean isDeliveryManager, String slackEmail) {
}
