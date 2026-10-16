package com.sparta.logistics.order.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.order.application.notification.SlackDirectMessageGateway;
import com.sparta.logistics.order.application.notification.SlackDmDeliveryException;
import com.sparta.logistics.order.application.notification.SlackDmDeliveryException.Reason;
import com.sparta.logistics.order.infrastructure.config.SlackDmConfig;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlackDmClientTest {
    private static final String EMAIL = "driver+team@example.com";
    private static final String TOKEN = "ui-test-bot-secret";

    @Test
    @DisplayName("이메일 인코딩·봇 인증으로 조회→DM 열기→정확한 채널 발송을 수행하고 사용자 헤더와 멘션을 전파하지 않는다")
    void fullProtocol() throws Exception {
        try (HttpStubServer stub = new HttpStubServer()) {
            stub.reset(this::success);
            assertThat(client(stub, List.of(), 1000).send(EMAIL, "상품 <@U123> & <!channel>")).isTrue();
            var lookup = stub.requests("GET", "/users.lookupByEmail").get(0);
            assertThat(lookup.rawQuery()).isEqualTo("email=driver%2Bteam%40example.com");
            assertThat(lookup.header("Authorization")).isEqualTo("Bearer " + TOKEN);
            for (String name : UserHeaders.ALL) assertThat(lookup.header(name)).isNull();
            var mapper = new ObjectMapper();
            assertThat(mapper.readTree(stub.requests("POST", "/conversations.open").get(0).body()).path("users").asText()).isEqualTo("UTEST1");
            var posted = stub.requests("POST", "/chat.postMessage");
            assertThat(posted).hasSize(1);
            var body = mapper.readTree(posted.get(0).body());
            assertThat(body.path("channel").asText()).isEqualTo("DTEST1");
            assertThat(body.path("text").asText()).isEqualTo("상품 &lt;@U123&gt; &amp; &lt;!channel&gt;");
            assertThat(body.path("mrkdwn").asBoolean(true)).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_scope", "users_not_found", "ratelimited", "unknown_error_with_secret"})
    @DisplayName("HTTP 200의 ok=false도 실패이며 Slack 원문·이메일·토큰을 예외에 넣지 않는다")
    void rejectsApiError(String error) {
        try (HttpStubServer stub = new HttpStubServer()) {
            stub.reset(request -> Reply.json(200, "{\"ok\":false,\"error\":\"" + error + "\"}"));
            assertThatThrownBy(() -> client(stub, List.of(), 1000).send(EMAIL, "테스트"))
                    .isInstanceOf(SlackDmDeliveryException.class).hasMessageNotContaining(error).hasMessageNotContaining(EMAIL).hasMessageNotContaining(TOKEN);
            assertThat(stub.requests("GET", "/users.lookupByEmail")).hasSize(1);
            assertThat(stub.requests("POST", "/")).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 429, 500})
    @DisplayName("HTTP 오류를 한 번만 처리하고 자동 재시도하지 않는다")
    void noHttpRetries(int status) {
        try (HttpStubServer stub = new HttpStubServer()) {
            stub.reset(request -> Reply.json(status, "{\"ok\":false}"));
            assertThatThrownBy(() -> client(stub, List.of(), 1000).send(EMAIL, "테스트"))
                    .isInstanceOf(SlackDmDeliveryException.class);
            assertThat(stub.requests("GET", "/users.lookupByEmail")).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-team", "deleted", "bot", "no-id", "bad-channel", "wrong-post-channel", "no-ts"})
    @DisplayName("워크스페이스·사용자·DM 채널·발송 영수증을 검증한다")
    void validatesRecipientAndReceipt(String mode) {
        try (HttpStubServer stub = new HttpStubServer()) {
            stub.reset(request -> {
                Reply normal = success(request);
                String body = normal.body();
                if (mode.equals("wrong-team")) body = body.replace("TTEST", "TOTHER");
                if (mode.equals("deleted")) body = body.replace("\"deleted\":false", "\"deleted\":true");
                if (mode.equals("bot")) body = body.replace("\"is_bot\":false", "\"is_bot\":true");
                if (mode.equals("no-id")) body = body.replace("UTEST1", "");
                if (mode.equals("bad-channel") && request.path().endsWith("conversations.open")) body = body.replace("DTEST1", "CTEST1");
                if (mode.equals("wrong-post-channel") && request.path().endsWith("chat.postMessage")) body = body.replace("DTEST1", "DOTHER");
                if (mode.equals("no-ts")) body = body.replace("100.001", "");
                return Reply.json(200, body);
            });
            assertThatThrownBy(() -> client(stub, List.of(), 1000).send(EMAIL, "테스트")).isInstanceOf(SlackDmDeliveryException.class);
            if (!mode.equals("wrong-post-channel") && !mode.equals("no-ts")) assertThat(stub.requests("POST", "/chat.postMessage")).isEmpty();
        }
    }

    @Test
    @DisplayName("POST 응답 유실은 발송 여부가 불확실하므로 한 번만 시도한다")
    void postTimeoutDoesNotDuplicate() {
        try (HttpStubServer stub = new HttpStubServer()) {
            stub.reset(request -> request.path().endsWith("chat.postMessage") ? success(request).delayed(1000) : success(request));
            assertThatThrownBy(() -> client(stub, List.of(), 100).send(EMAIL, "테스트"))
                    .isInstanceOf(SlackDmDeliveryException.class).extracting("reason").isEqualTo(Reason.TRANSPORT_FAILURE);
            assertThat(stub.requests("POST", "/chat.postMessage")).hasSize(1);
        }
    }

    @Test
    @DisplayName("허용되지 않은 이메일과 비활성 설정은 외부 호출 없이 생략한다")
    void honorsConfiguration() {
        try (HttpStubServer stub = new HttpStubServer()) {
            assertThat(client(stub, List.of("different@example.com"), 1000).send(EMAIL, "테스트")).isFalse();
            var disabled = new SlackDmProperties(false, TOKEN, "TTEST", stub.url(), List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1));
            assertThat(new SlackDmConfig().slackDirectMessageGateway(disabled).send(EMAIL, "테스트")).isFalse();
            assertThat(stub.requests("GET", "/")).isEmpty();
            assertThat(disabled.toString()).doesNotContain(TOKEN, EMAIL);
        }
    }

    private SlackDirectMessageGateway client(HttpStubServer stub, List<String> allowed, int timeout) {
        return new SlackDmConfig().slackDirectMessageGateway(new SlackDmProperties(true, TOKEN, "TTEST", stub.url(), allowed, Duration.ofSeconds(1), Duration.ofMillis(timeout)));
    }
    private Reply success(HttpStubServer.RecordedRequest request) {
        return switch (request.path()) {
            case "/users.lookupByEmail" -> Reply.json(200, "{\"ok\":true,\"user\":{\"id\":\"UTEST1\",\"team_id\":\"TTEST\",\"deleted\":false,\"is_bot\":false}}");
            case "/conversations.open" -> Reply.json(200, "{\"ok\":true,\"channel\":{\"id\":\"DTEST1\"}}");
            case "/chat.postMessage" -> Reply.json(200, "{\"ok\":true,\"channel\":\"DTEST1\",\"ts\":\"100.001\"}");
            default -> Reply.json(404, "{}");
        };
    }
}
