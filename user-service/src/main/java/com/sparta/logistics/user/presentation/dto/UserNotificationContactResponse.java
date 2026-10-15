package com.sparta.logistics.user.presentation.dto;

import com.sparta.logistics.user.application.dto.UserNotificationContactResult;
import java.util.UUID;

/** I-16 전용 연락처. 로그인·권한 재확인 응답에는 이메일을 노출하지 않는다. */
public record UserNotificationContactResponse(UUID userId, boolean isDeliveryManager, String slackEmail) {
    public static UserNotificationContactResponse from(UserNotificationContactResult result) {
        return new UserNotificationContactResponse(result.userId(), result.isDeliveryManager(), result.slackEmail());
    }
}
