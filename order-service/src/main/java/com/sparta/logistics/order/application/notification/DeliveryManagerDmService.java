package com.sparta.logistics.order.application.notification;

import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryManagerDmService {
    private static final Set<String> ACTIVE = Set.of("PICKUP_WAITING", "PICKING_UP", "HUB_WAITING",
            "HUB_MOVING", "DEST_HUB_ARRIVED", "OUT_FOR_DELIVERY");
    private final NotificationDirectory directory;
    private final SlackDirectMessageGateway messages;

    /** 커밋된 주문의 현재 배송 담당자에게 보낸다. 원격 호출 동안 주문 DB 트랜잭션을 열지 않는다. */
    public void notifyManagers(OrderCreatedEvent event) {
        NotificationDirectory.Targets targets = directory.findTargets(event.deliveryId());
        if (targets == null || !event.deliveryId().equals(targets.deliveryId())
                || !event.orderId().equals(targets.orderId()) || targets.managerIds() == null
                || targets.managerIds().size() > 3 || targets.managerIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new SlackDmDeliveryException(SlackDmDeliveryException.Reason.INVALID_RESPONSE);
        }
        if (targets.status() == null || !ACTIVE.contains(targets.status())) return;
        Set<String> attemptedEmails = new HashSet<>();
        for (UUID userId : new HashSet<>(targets.managerIds())) {
            try {
                NotificationDirectory.Contact contact = directory.findContact(userId);
                if (contact == null || !userId.equals(contact.userId())) {
                    throw new SlackDmDeliveryException(SlackDmDeliveryException.Reason.INVALID_RESPONSE);
                }
                if (!contact.isDeliveryManager() || !StringUtils.hasText(contact.slackEmail())) {
                    log.info("배송 DM 생략: orderId={}, userId={}, reason=CONTACT_NOT_CONFIGURED", event.orderId(), userId);
                    continue;
                }
                String email = contact.slackEmail().strip();
                // Note: 응답 유실도 발송됐을 수 있어 같은 수신자의 재시도를 하지 않는다. 같은 이벤트 안에서만 중복을 막는다.
                if (!attemptedEmails.add(email.toLowerCase(Locale.ROOT))) continue;
                boolean sent = messages.send(email, message(event));
                log.info("배송 DM 결과: orderId={}, userId={}, status={}", event.orderId(), userId,
                        sent ? "SLACK_DM_SENT" : "SLACK_DM_NOT_ALLOWED");
            } catch (RuntimeException exception) {
                String reason = exception instanceof SlackDmDeliveryException failure
                        ? failure.reason().name() : exception.getClass().getSimpleName();
                log.warn("배송 DM 실패: orderId={}, userId={}, reason={}", event.orderId(), userId, reason);
            }
        }
    }

    private String message(OrderCreatedEvent event) {
        return "[신규 주문 배송 배정]\n주문: %s\n배송: %s\n공급업체: %s → 수령업체: %s\n상품: %s x %d\n요청사항: %s"
                .formatted(event.orderId(), event.deliveryId(), event.supplierCompanyName(), event.receiverCompanyName(),
                        event.productName(), event.quantity(), StringUtils.hasText(event.requestNote()) ? event.requestNote() : "없음");
    }
}
