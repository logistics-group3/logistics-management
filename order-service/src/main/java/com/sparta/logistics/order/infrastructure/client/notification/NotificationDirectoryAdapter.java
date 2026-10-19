package com.sparta.logistics.order.infrastructure.client.notification;

import com.sparta.logistics.order.application.notification.NotificationDirectory;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDirectoryAdapter implements NotificationDirectory {
    private final NotificationDeliveryClient deliveries;
    private final NotificationUserClient users;

    @Override
    public Targets findTargets(UUID deliveryId) {
        NotificationDeliveryClient.Response response = deliveries.get(deliveryId);
        return response == null ? null : new Targets(response.deliveryId(), response.orderId(), response.status(), response.managerIds());
    }

    @Override
    public Contact findContact(UUID userId) {
        NotificationUserClient.Response response = users.get(userId);
        return response == null ? null : new Contact(response.userId(), response.isDeliveryManager(), response.slackEmail());
    }
}
