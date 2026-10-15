package com.sparta.logistics.order.application.event;

import com.sparta.logistics.order.application.notification.DeliveryManagerDmSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "slack.dm", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DeliveryManagerDmEventListener {
    private final DeliveryManagerDmSender sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            sender.send(event);
        } catch (RuntimeException exception) {
            log.warn("배송 DM 작업 제출 실패: orderId={}, reason={}", event.orderId(), exception.getClass().getSimpleName());
        }
    }
}
