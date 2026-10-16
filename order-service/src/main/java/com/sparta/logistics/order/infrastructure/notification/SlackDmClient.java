package com.sparta.logistics.order.infrastructure.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.logistics.order.application.notification.SlackDirectMessageGateway;
import com.sparta.logistics.order.application.notification.SlackDmDeliveryException;
import com.sparta.logistics.order.application.notification.SlackDmDeliveryException.Reason;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Slack 전용 HTTP 클라이언트. 사용자 JWT·Feign 재시도를 전달하지 않는다. */
public class SlackDmClient implements SlackDirectMessageGateway {
    private final RestClient client;
    private final SlackDmProperties properties;

    public SlackDmClient(RestClient client, SlackDmProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean send(String email, String message) {
        if (!properties.configured() || !properties.allows(email)) return false;
        Reply lookup = call(() -> client.get().uri("/users.lookupByEmail?email={email}", email).retrieve().body(Reply.class));
        SlackUser user = lookup.user();
        if (user == null || user.id() == null || !user.id().matches("[UW][A-Z0-9]+")) fail(Reason.INVALID_RESPONSE);
        if (user.deleted() || user.bot()) fail(Reason.RECIPIENT_UNAVAILABLE);
        if (!properties.workspaceId().equals(user.teamId())) fail(Reason.WORKSPACE_MISMATCH);
        Reply opened = call(() -> client.post().uri("/conversations.open").body(Map.of("users", user.id())).retrieve().body(Reply.class));
        String channel = opened.channel() == null ? null : opened.channel().path("id").asText(null);
        if (channel == null || !channel.matches("D[A-Z0-9]+")) fail(Reason.INVALID_RESPONSE);
        String safeText = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        Reply posted = call(() -> client.post().uri("/chat.postMessage")
                .body(Map.of("channel", channel, "text", safeText, "mrkdwn", false, "unfurl_links", false, "unfurl_media", false))
                .retrieve().body(Reply.class));
        if (posted.ts() == null || posted.ts().isBlank() || posted.channel() == null || !channel.equals(posted.channel().asText())) fail(Reason.INVALID_RESPONSE);
        return true;
    }

    private Reply call(Supplier<Reply> operation) {
        try {
            Reply reply = operation.get();
            if (reply == null) fail(Reason.INVALID_RESPONSE);
            if (!reply.ok()) fail(reason(reply.error()));
            return reply;
        } catch (SlackDmDeliveryException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            fail(exception.getStatusCode().value() == 429 ? Reason.RATE_LIMITED : Reason.HTTP_FAILURE);
        } catch (RuntimeException exception) {
            fail(Reason.TRANSPORT_FAILURE);
        }
        throw new IllegalStateException("unreachable");
    }

    private Reason reason(String error) {
        if (error == null) return Reason.INVALID_RESPONSE;
        return switch (error) {
            case "users_not_found" -> Reason.USER_NOT_FOUND;
            case "missing_scope", "invalid_auth", "not_authed", "token_revoked", "account_inactive" -> Reason.PERMISSION_DENIED;
            case "ratelimited" -> Reason.RATE_LIMITED;
            default -> Reason.SLACK_API_ERROR;
        };
    }
    private static void fail(Reason reason) { throw new SlackDmDeliveryException(reason); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Reply(boolean ok, String error, SlackUser user, JsonNode channel, String ts) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SlackUser(String id, @JsonProperty("team_id") String teamId, boolean deleted, @JsonProperty("is_bot") boolean bot) { }
}
