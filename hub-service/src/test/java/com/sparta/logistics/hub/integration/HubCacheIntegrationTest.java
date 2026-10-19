package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.application.HubQueryService;
import com.sparta.logistics.hub.application.HubRouteQueryService;
import com.sparta.logistics.hub.application.dto.InternalHubRouteResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * hub.cache.type=local에서 단건 조회가 캐시되는지 확인한다(성능 측정 항목 B1-local).
 * Note: 첫 조회 뒤 DB 값을 직접 바꾸고 다시 조회한다. 바뀌기 전 값이 나오면 DB가 아니라 캐시에서 읽은 것이다.
 */
@Transactional
@TestPropertySource(properties = "hub.cache.type=local")
class HubCacheIntegrationTest extends PostgresIntegrationTest {

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
    @DisplayName("허브 단건 조회(H-02)는 두 번째부터 캐시에서 읽는다")
    void hubIsCached() {
        UUID hubId = jdbcTemplate.queryForObject("SELECT hub_id FROM p_hubs ORDER BY name LIMIT 1", UUID.class);
        String name = hubQueryService.getHub(hubId).name();

        jdbcTemplate.update("UPDATE p_hubs SET name = '바뀐 이름' WHERE hub_id = ?", hubId);

        assertThat(hubQueryService.getHub(hubId).name()).isEqualTo(name);
    }

    @Test
    @DisplayName("I-03 경로 조회는 출발·도착 허브 쌍을 키로 캐시한다")
    void internalRouteIsCachedByHubPair() {
        Map<String, Object> route = jdbcTemplate.queryForMap(
                "SELECT departure_hub_id, arrival_hub_id, duration_minutes FROM p_hub_routes ORDER BY hub_route_id LIMIT 1");
        UUID departure = (UUID) route.get("departure_hub_id");
        UUID arrival = (UUID) route.get("arrival_hub_id");
        InternalHubRouteResult first = hubRouteQueryService.getInternalHubRoute(departure, arrival);

        jdbcTemplate.update("UPDATE p_hub_routes SET duration_minutes = duration_minutes + 100 WHERE departure_hub_id = ? AND arrival_hub_id = ?",
                departure, arrival);

        assertThat(hubRouteQueryService.getInternalHubRoute(departure, arrival)).isEqualTo(first);
        assertThat(hubRouteQueryService.getInternalHubRoute(arrival, departure).durationMinutes()).isPositive();
    }
}
