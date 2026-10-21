package com.sparta.logistics.order.infrastructure.notification;

import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class SlackOrderNotificationSender implements OrderNotificationSender {

    private final RestClient webhookClient;
    private final SlackProperties properties;

    @Override
    @Async("orderNotificationExecutor")
    public void send(OrderCreatedEvent event) {
        post(event.orderId(), SlackMessage.from(event));
    }

    /** Note: 채널 Webhook이라 MASTER가 누구인지 몰라도 된다. MASTER는 이 채널에 들어와 있어야 한다. */
    @Override
    @Async("orderNotificationExecutor")
    public void sendManualAssignmentRequired(UUID orderId, UUID deliveryId) {
        post(orderId, SlackMessage.escaped("[배송담당자 수동 지정 필요] 주문: %s\n배송: %s\n배정할 배송담당자가 없습니다. 배송 관리에서 구간 담당자를 지정해 주세요."
                .formatted(orderId, deliveryId)));
    }

    private void post(UUID orderId, SlackMessage message) {
        if (!properties.configured()) {
            log.debug("Slack 미설정으로 주문 알림 생략: orderId={}", orderId);
            return;
        }
        try {
            webhookClient.post()
                    .uri(URI.create(properties.webhookUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.error("주문 Slack 알림 실패: orderId={}, status=SLACK_NOTIFICATION_FAILED, httpStatus={}",
                                    orderId, response.getStatusCode().value());
                        }
                        return null;
                    });
        } catch (RuntimeException exception) {
            // Note: HTTP 예외 메시지·스택에는 비밀 Webhook URL이 들어갈 수 있어 종류와 주문 ID만 기록한다.
            log.error("주문 Slack 알림 실패: orderId={}, status=SLACK_NOTIFICATION_FAILED, reason={}",
                    orderId, exception.getClass().getSimpleName());
        }
    }

    private record SlackMessage(String text, boolean mrkdwn) {

        static SlackMessage from(OrderCreatedEvent event) {
            String note = StringUtils.hasText(event.requestNote()) ? event.requestNote() : "없음";
            String text = "[신규 주문] %s\n공급업체: %s → 수령업체: %s\n상품: %s x %d\n요청사항: %s"
                    .formatted(event.orderId(), event.supplierCompanyName(), event.receiverCompanyName(),
                            event.productName(), event.quantity(), note);
            return escaped(text);
        }

        static SlackMessage escaped(String text) {
            // Note: 사용자 입력의 <!channel> 등이 Slack 멘션으로 해석되지 않게 제어 문자를 이스케이프한다.
            return new SlackMessage(text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"), false);
        }
    }
}
