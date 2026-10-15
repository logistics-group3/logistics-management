package com.sparta.logistics.order.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.infrastructure.config.OrderNotificationConfig;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class SlackOrderNotificationSenderTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Webhook URL이 없으면 외부 요청 없이 알림을 생략한다")
    void skipsUnconfiguredWebhook(String url) {
        RestClient client = mock(RestClient.class);
        SlackOrderNotificationSender sender = new SlackOrderNotificationSender(client, properties(url));

        sender.send(event(null));

        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("요청사항이 없으면 없음으로 표시하고 JSON Webhook을 한 번 보낸다")
    void formatsAbsentRequestNote(String note) throws Exception {
        try (HttpStubServer slack = new HttpStubServer()) {
            slack.reset(request -> Reply.json(200, "ok"));
            SlackOrderNotificationSender sender = new OrderNotificationConfig()
                    .slackOrderNotificationSender(properties(slack.url() + "/test"));

            sender.send(event(note));

            assertThat(slack.requests("POST", "/test")).hasSize(1);
            var payload = new ObjectMapper().readTree(slack.requests("POST", "/test").get(0).body());
            assertThat(payload.path("text").asText()).endsWith("요청사항: 없음");
        }
    }

    @Test
    @DisplayName("설정을 문자열로 출력해도 비밀 Webhook URL을 노출하지 않는다")
    void redactsWebhookFromProperties() {
        assertThat(properties("https://example.invalid/secret-token").toString())
                .contains("configured=true").doesNotContain("example.invalid", "secret-token");
    }

    private SlackProperties properties(String url) {
        return new SlackProperties(url, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private OrderCreatedEvent event(String requestNote) {
        return new OrderCreatedEvent(UUID.randomUUID(), "공급업체", "수령업체", "상품", 50, requestNote);
    }
}
