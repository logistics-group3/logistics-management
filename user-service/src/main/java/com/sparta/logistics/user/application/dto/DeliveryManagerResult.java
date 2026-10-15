package com.sparta.logistics.user.application.dto;

import com.sparta.logistics.user.domain.User;
import java.util.UUID;

/** I-14 응답 항목. delivery-service가 담당자를 배정하고 Slack으로 알리는 데 필요한 값만 담는다. */
public record DeliveryManagerResult(UUID userId, String slackId) {

    public static DeliveryManagerResult from(User user) {
        return new DeliveryManagerResult(user.getId(), user.getSlackId());
    }
}
