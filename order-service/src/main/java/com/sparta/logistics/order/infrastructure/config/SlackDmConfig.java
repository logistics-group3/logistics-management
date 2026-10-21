package com.sparta.logistics.order.infrastructure.config;

import com.sparta.logistics.order.application.notification.AssignmentNotificationHandler;
import com.sparta.logistics.order.application.notification.SlackDirectMessageGateway;
import com.sparta.logistics.order.infrastructure.notification.AsyncAssignmentNotificationSender;
import com.sparta.logistics.order.infrastructure.notification.SlackDmClient;
import com.sparta.logistics.order.infrastructure.notification.SlackDmProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SlackDmProperties.class)
public class SlackDmConfig {
    @Bean
    public SlackDirectMessageGateway slackDirectMessageGateway(SlackDmProperties properties) {
        // Note: 다른 주소로의 리다이렉트에 봇 토큰을 전달하지 않는다. 쓰기 요청 자동 재시도도 두지 않는다.
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        RestClient client = RestClient.builder().baseUrl(properties.apiBaseUrl()).requestFactory(factory)
                .defaultHeaders(headers -> headers.setBearerAuth(properties.botToken()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).build();
        return new SlackDmClient(client, properties);
    }

    /** I-20 배정 결과 알림(delivery 결정 로그 29). 배정 완료 DM은 주문 커밋 직후가 아니라 담당자 배정이 끝났을 때 보낸다. */
    @Bean
    public AsyncAssignmentNotificationSender assignmentNotificationSender(SlackDmProperties properties,
                                                                         AssignmentNotificationHandler handler) {
        return new AsyncAssignmentNotificationSender(properties, handler);
    }
}
