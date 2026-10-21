package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.application.HubCacheNames;
import com.sparta.logistics.hub.application.HubQueryService;
import com.sparta.logistics.hub.application.HubRouteQueryService;
import com.sparta.logistics.hub.application.dto.HubResult;
import com.sparta.logistics.hub.application.dto.HubRouteResult;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;

/**
 * hub.cache.type=redis에서 결과가 JSON으로 저장되고 같은 타입으로 되읽히는지 확인한다(성능 측정 항목 B1-redis).
 * Note: 로컬 캐시와 달리 Redis는 값을 직렬화한다. BigDecimal 좌표·거리와 중첩 record가 그대로 돌아오는지가 핵심이다.
 */
@Transactional
@TestPropertySource(properties = "hub.cache.type=redis")
class HubRedisCacheIntegrationTest extends PostgresIntegrationTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private HubQueryService hubQueryService;
    @Autowired
    private HubRouteQueryService hubRouteQueryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("Redis 캐시 관리자로 뜨고, 허브 조회 결과가 같은 값으로 되읽힌다")
    void hubRoundTrip() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        UUID hubId = jdbcTemplate.queryForObject("SELECT hub_id FROM p_hubs ORDER BY name LIMIT 1", UUID.class);
        HubResult first = hubQueryService.getHub(hubId);

        jdbcTemplate.update("UPDATE p_hubs SET name = '바뀐 이름' WHERE hub_id = ?", hubId);

        assertThat(hubQueryService.getHub(hubId)).isEqualTo(first);
        assertThat(cacheManager.getCache(HubCacheNames.HUB).get(hubId, HubResult.class)).isEqualTo(first);
    }

    @Test
    @DisplayName("중첩 record가 든 경로 단건(H-04)도 되읽힌다")
    void routeRoundTrip() {
        UUID routeId = jdbcTemplate.queryForObject("SELECT hub_route_id FROM p_hub_routes ORDER BY hub_route_id LIMIT 1", UUID.class);
        HubRouteResult first = hubRouteQueryService.getHubRoute(routeId);

        assertThat(cacheManager.getCache(HubCacheNames.HUB_ROUTE).get(routeId, HubRouteResult.class)).isEqualTo(first);
    }
}
