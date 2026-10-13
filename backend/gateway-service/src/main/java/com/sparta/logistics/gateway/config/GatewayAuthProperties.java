package com.sparta.logistics.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gateway.auth.* (config/gateway-service.yml)
 * <p>JavaBean(setter) 방식: 설정 파일이 바뀌면 /actuator/refresh로 재시작 없이 다시 바인딩된다.
 * (record·생성자 바인딩은 refresh 때 바뀌지 않는다) 사용하는 쪽은 매 요청마다 getter로 읽는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    /** 인증 없이 통과하는 경로 (Ant 패턴) */
    private List<String> publicPaths = new ArrayList<>();

    /** 권한 재확인 결과 캐시 시간 (Notion 공통 인프라 범위: 60초) */
    private Duration roleCacheTtl = Duration.ofSeconds(60);

    /** user-service 내부 API 호출 타임아웃 */
    private Duration userLookupTimeout = Duration.ofSeconds(3);
}
