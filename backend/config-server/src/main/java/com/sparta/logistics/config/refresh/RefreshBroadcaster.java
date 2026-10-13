package com.sparta.logistics.config.refresh;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Eureka에서 대상 서비스의 모든 인스턴스를 찾아 {@code POST /actuator/refresh}를 호출한다.
 * 인스턴스는 config-server에서 새 설정을 다시 받아 @ConfigurationProperties·@RefreshScope·Gateway 라우트를 갱신한다 (재시작 없음).
 * 관리 포트가 따로 있으면(gateway: management.server.port) Eureka 메타데이터 {@code management.port}를 쓴다.
 */
@Slf4j
@Component
public class RefreshBroadcaster {

    static final String MANAGEMENT_PORT = "management.port";

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;
    private final String self;

    public RefreshBroadcaster(DiscoveryClient discoveryClient, ConfigWatchProperties properties,
                              @Value("${spring.application.name}") String self) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.discoveryClient = discoveryClient;
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.self = self;
    }

    public void refresh(Collection<String> changedFiles) {
        Set<String> targets = RefreshTargets.resolve(changedFiles, discoveryClient.getServices(), self);
        if (targets.isEmpty()) {
            log.info("설정 변경 {} → refresh할 실행 중인 서비스 없음", changedFiles);
            return;
        }
        log.info("설정 변경 {} → refresh {}", changedFiles, targets);
        for (String service : targets) {
            for (ServiceInstance instance : discoveryClient.getInstances(service)) {
                refresh(service, instance);
            }
        }
    }

    private void refresh(String service, ServiceInstance instance) {
        URI uri = refreshUri(instance);
        try {
            // Content-Type을 지정하지 않으면 HttpURLConnection이 form-urlencoded를 붙여 actuator가 415로 거절한다
            String changedKeys = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            log.info("refresh 완료 {} ({}) 변경 키={}", service, uri.getAuthority(), changedKeys);
        } catch (Exception e) {
            log.warn("refresh 실패 {} ({}): {} — 이 인스턴스는 재시작해야 새 설정을 받습니다", service, uri, e.getMessage());
        }
    }

    static URI refreshUri(ServiceInstance instance) {
        String managementPort = instance.getMetadata().get(MANAGEMENT_PORT);
        int port = managementPort != null ? Integer.parseInt(managementPort) : instance.getPort();
        return URI.create("http://" + instance.getHost() + ":" + port + "/actuator/refresh");
    }
}
