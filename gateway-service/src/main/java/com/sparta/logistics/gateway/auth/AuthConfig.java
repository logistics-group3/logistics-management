package com.sparta.logistics.gateway.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})
public class AuthConfig {

    /** lb://user-service 를 Eureka 목록으로 풀어 주는 WebClient. */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public UserLookup userLookup(WebClient.Builder loadBalancedWebClientBuilder, AuthProperties properties) {
        UserServiceClient client = new UserServiceClient(loadBalancedWebClientBuilder.baseUrl("http://user-service").build());
        return new CachedUserLookup(client::fetch, properties.userCacheTtl());
    }
}
