package com.sparta.logistics.gateway.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(List<String> whitelist, Duration userCacheTtl) {
}
