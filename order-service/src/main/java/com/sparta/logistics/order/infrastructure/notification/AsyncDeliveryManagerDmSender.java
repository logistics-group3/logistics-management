package com.sparta.logistics.order.infrastructure.notification;

import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.application.notification.DeliveryManagerDmSender;
import com.sparta.logistics.order.application.notification.DeliveryManagerDmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@RequiredArgsConstructor
public class AsyncDeliveryManagerDmSender implements DeliveryManagerDmSender {
    private final SlackDmProperties properties;
    private final DeliveryManagerDmService service;

    @Override
    @Async("orderNotificationExecutor")
    public void send(OrderCreatedEvent event) {
        if (!properties.configured()) return;
        try {
            service.notifyManagers(event);
        } catch (RuntimeException exception) {
            // Note: 하위 HTTP 오류에는 비밀값·이메일이 포함될 수 있어 메시지·스택을 기록하지 않는다.
            log.warn("배송 DM 수신자 조회 실패: orderId={}, reason={}", event.orderId(), exception.getClass().getSimpleName());
        }
    }
}
