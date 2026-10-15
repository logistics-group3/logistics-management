package com.sparta.logistics.order.infrastructure.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "slack")
public record SlackProperties(@DefaultValue("") String webhookUrl,
                              @DefaultValue("1s") Duration connectTimeout,
                              @DefaultValue("2s") Duration readTimeout) {

    public boolean configured() {
        return StringUtils.hasText(webhookUrl);
    }

    @Override
    public String toString() {
        return "SlackProperties[configured=" + configured() + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
