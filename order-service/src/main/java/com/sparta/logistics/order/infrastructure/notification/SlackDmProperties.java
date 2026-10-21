package com.sparta.logistics.order.infrastructure.notification;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "slack.dm")
public record SlackDmProperties(@DefaultValue("false") boolean enabled,
                                @DefaultValue("") String botToken,
                                @DefaultValue("") String workspaceId,
                                @DefaultValue("https://slack.com/api") String apiBaseUrl,
                                List<String> allowedEmails,
                                @DefaultValue("1s") Duration connectTimeout,
                                @DefaultValue("2s") Duration readTimeout) {
    public SlackDmProperties {
        allowedEmails = allowedEmails == null ? List.of() : allowedEmails.stream()
                .filter(StringUtils::hasText).map(String::strip).toList();
    }
    public boolean configured() { return enabled && StringUtils.hasText(botToken) && StringUtils.hasText(workspaceId); }
    public boolean allows(String email) { return allowedEmails.isEmpty() || allowedEmails.stream().anyMatch(value -> value.equalsIgnoreCase(email)); }
    @Override public String toString() { return "SlackDmProperties[enabled=" + enabled + ", configured=" + configured() + ", allowedEmailCount=" + allowedEmails.size() + "]"; }
}
