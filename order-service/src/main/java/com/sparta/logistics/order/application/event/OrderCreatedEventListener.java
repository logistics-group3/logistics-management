package com.sparta.logistics.order.application.event;

import com.sparta.logistics.order.application.OrderNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventListener {

    private final OrderNotificationSender notificationSender;

    /** O-01 저장 트랜잭션이 성공한 이벤트만 알림 작업으로 넘긴다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            // Note: @Async는 별도 빈의 프록시를 거쳐야 한다. 큐 포화 시 제출 단계에서 나는 예외도 격리한다.
            notificationSender.send(event);
        } catch (RuntimeException exception) {
            log.error("주문 알림 작업 제출 실패: orderId={}, status=NOTIFICATION_SUBMISSION_FAILED, reason={}",
                    event.orderId(), exception.getClass().getSimpleName());
        }
    }
}
