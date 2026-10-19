package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostgreSQL에서 V1~V3 마이그레이션이 적재한 허브 17개·경로 272건으로 H-03, H-04를 HTTP까지 확인한다.
 * Note: 시드 데이터를 지우거나 바꾸지 않고 읽기만 하므로 트랜잭션 롤백이 필요 없다.
 */
@AutoConfigureMockMvc
class HubRouteQueryIntegrationTest extends PostgresIntegrationTest {

    private static final String SEOUL_HUB_ID = "6f39d519-4947-4a95-a454-58c25728f388";
    private static final String BUSAN_HUB_ID = "47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("필터 없이 조회하면 시드 경로 272건 전체가 나오고 기본 정렬은 createdAt,DESC")
    void listAll() throws Exception {
        mockMvc.perform(get("/api/v1/hub-routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(272))
                .andExpect(jsonPath("$.data.totalPages").value(28))
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.sort").value("createdAt,DESC"));
    }

    @Test
    @DisplayName("출발 허브로 거르면 그 허브에서 가는 경로 16건만 나오고 모두 출발 허브가 같다")
    void filterByDeparture() throws Exception {
        String body = mockMvc.perform(get("/api/v1/hub-routes").param("departureHubId", SEOUL_HUB_ID).param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(16))
                .andReturn().getResponse().getContentAsString();

        List<String> departureIds = JsonPath.read(body, "$.data.content[*].departureHub.hubId");
        assertThat(departureIds).hasSize(16).containsOnly(SEOUL_HUB_ID);
    }

    @Test
    @DisplayName("도착 허브로 거르면 그 허브로 오는 경로 16건만 나온다")
    void filterByArrival() throws Exception {
        String body = mockMvc.perform(get("/api/v1/hub-routes").param("arrivalHubId", BUSAN_HUB_ID).param("size", "30"))
                .andExpect(jsonPath("$.data.totalElements").value(16))
                .andReturn().getResponse().getContentAsString();

        List<String> arrivalIds = JsonPath.read(body, "$.data.content[*].arrivalHub.hubId");
        assertThat(arrivalIds).containsOnly(BUSAN_HUB_ID);
    }

    @Test
    @DisplayName("출발과 도착을 모두 주면 방향이 있는 경로 1건이고 허브명과 거리·시간이 저장값과 같다")
    void filterByBoth() throws Exception {
        BigDecimal distance = jdbcTemplate.queryForObject(
                "SELECT distance_km FROM p_hub_routes WHERE departure_hub_id = ?::uuid AND arrival_hub_id = ?::uuid",
                BigDecimal.class, SEOUL_HUB_ID, BUSAN_HUB_ID);
        Integer duration = jdbcTemplate.queryForObject(
                "SELECT duration_minutes FROM p_hub_routes WHERE departure_hub_id = ?::uuid AND arrival_hub_id = ?::uuid",
                Integer.class, SEOUL_HUB_ID, BUSAN_HUB_ID);

        mockMvc.perform(get("/api/v1/hub-routes").param("departureHubId", SEOUL_HUB_ID).param("arrivalHubId", BUSAN_HUB_ID))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].departureHub.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.content[0].arrivalHub.name").value("부산광역시 센터"))
                .andExpect(jsonPath("$.data.content[0].distanceKm").value(distance.doubleValue()))
                .andExpect(jsonPath("$.data.content[0].durationMinutes").value(duration));
    }

    @Test
    @DisplayName("존재하지 않는 허브 ID로 거르면 404가 아니라 빈 페이지")
    void unknownHubGivesEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/hub-routes").param("departureHubId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("distanceKm 오름차순 정렬의 첫 항목은 전체에서 가장 짧은 거리이고 내림차순은 가장 긴 거리이다")
    void sortByDistance() throws Exception {
        BigDecimal min = jdbcTemplate.queryForObject("SELECT MIN(distance_km) FROM p_hub_routes", BigDecimal.class);
        BigDecimal max = jdbcTemplate.queryForObject("SELECT MAX(distance_km) FROM p_hub_routes", BigDecimal.class);

        mockMvc.perform(get("/api/v1/hub-routes").param("sort", "distanceKm,asc"))
                .andExpect(jsonPath("$.data.content[0].distanceKm").value(min.doubleValue()));
        mockMvc.perform(get("/api/v1/hub-routes").param("sort", "distanceKm,desc"))
                .andExpect(jsonPath("$.data.content[0].distanceKm").value(max.doubleValue()));
    }

    @Test
    @Transactional
    @DisplayName("createdAt 정렬은 방향을 따르고, 시각이 같은 시드 경로는 ID 순으로 안정적으로 나온다")
    void sortByCreatedAt() throws Exception {
        // Note: 시드 행은 한 마이그레이션 트랜잭션에서 NOW()로 들어가 created_at이 모두 같다. 방향을 보려면 두 행의 시각만 바꿔야 하므로
        // 메서드를 트랜잭션으로 묶어 롤백한다(MockMvc는 같은 스레드라 미커밋 변경을 본다).
        String oldest = routeId(SEOUL_HUB_ID, BUSAN_HUB_ID);
        String newest = routeId(BUSAN_HUB_ID, SEOUL_HUB_ID);
        jdbcTemplate.update("UPDATE p_hub_routes SET created_at = NOW() - INTERVAL '1 day' WHERE hub_route_id = ?::uuid", oldest);
        jdbcTemplate.update("UPDATE p_hub_routes SET created_at = NOW() + INTERVAL '1 day' WHERE hub_route_id = ?::uuid", newest);

        mockMvc.perform(get("/api/v1/hub-routes").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sort").value("createdAt,ASC"))
                .andExpect(jsonPath("$.data.content[0].hubRouteId").value(oldest));
        mockMvc.perform(get("/api/v1/hub-routes").param("sort", "createdAt,desc"))
                .andExpect(jsonPath("$.data.sort").value("createdAt,DESC"))
                .andExpect(jsonPath("$.data.content[0].hubRouteId").value(newest));
        // 기본 정렬(createdAt,DESC)도 같은 결과다.
        mockMvc.perform(get("/api/v1/hub-routes"))
                .andExpect(jsonPath("$.data.content[0].hubRouteId").value(newest));
    }

    @Test
    @DisplayName("같은 정렬 기준에서 페이지를 나눠 조회해도 항목이 겹치거나 빠지지 않는다")
    void pagesDoNotOverlap() throws Exception {
        List<String> all = new java.util.ArrayList<>();
        for (int page = 0; page < 6; page++) {
            String body = mockMvc.perform(get("/api/v1/hub-routes")
                            .param("sort", "durationMinutes,asc").param("page", String.valueOf(page)).param("size", "50"))
                    .andReturn().getResponse().getContentAsString();
            all.addAll(JsonPath.read(body, "$.data.content[*].hubRouteId"));
        }

        // 50건씩 6페이지 = 272건 + 빈 페이지. 모든 경로가 정확히 한 번씩 나온다.
        assertThat(all).hasSize(272).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("허용 목록에 없는 정렬 필드는 400 VALIDATION_ERROR")
    void disallowedSort() throws Exception {
        mockMvc.perform(get("/api/v1/hub-routes").param("sort", "name,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("H-04는 두 허브명과 거리·시간을 반환하고, 없는 경로는 404 HUB_ROUTE_NOT_FOUND")
    void getHubRoute() throws Exception {
        UUID hubRouteId = UUID.fromString(routeId(SEOUL_HUB_ID, BUSAN_HUB_ID));

        mockMvc.perform(get("/api/v1/hub-routes/{hubRouteId}", hubRouteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hubRouteId").value(hubRouteId.toString()))
                .andExpect(jsonPath("$.data.departureHub.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.arrivalHub.name").value("부산광역시 센터"));
        mockMvc.perform(get("/api/v1/hub-routes/{hubRouteId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("I-03은 감싸지 않은 본문으로 출발→도착 경로 1건의 ID·거리·시간만 반환하고 방향을 구분한다")
    void internalGetHubRoute() throws Exception {
        String seoulToBusan = routeId(SEOUL_HUB_ID, BUSAN_HUB_ID);
        String busanToSeoul = routeId(BUSAN_HUB_ID, SEOUL_HUB_ID);
        BigDecimal distance = jdbcTemplate.queryForObject(
                "SELECT distance_km FROM p_hub_routes WHERE hub_route_id = ?::uuid", BigDecimal.class, seoulToBusan);
        Integer duration = jdbcTemplate.queryForObject(
                "SELECT duration_minutes FROM p_hub_routes WHERE hub_route_id = ?::uuid", Integer.class, seoulToBusan);

        mockMvc.perform(get("/internal/hub-routes").param("departureHubId", SEOUL_HUB_ID).param("arrivalHubId", BUSAN_HUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubRouteId").value(seoulToBusan))
                .andExpect(jsonPath("$.distanceKm").value(distance.doubleValue()))
                .andExpect(jsonPath("$.durationMinutes").value(duration))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.departureHub").doesNotExist());
        mockMvc.perform(get("/internal/hub-routes").param("departureHubId", BUSAN_HUB_ID).param("arrivalHubId", SEOUL_HUB_ID))
                .andExpect(jsonPath("$.hubRouteId").value(busanToSeoul));
    }

    @Test
    @DisplayName("I-03에서 같은 허브 쌍이거나 없는 허브면 404 HUB_ROUTE_NOT_FOUND")
    void internalNotFound() throws Exception {
        mockMvc.perform(get("/internal/hub-routes").param("departureHubId", SEOUL_HUB_ID).param("arrivalHubId", SEOUL_HUB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_ROUTE_NOT_FOUND"));
        mockMvc.perform(get("/internal/hub-routes").param("departureHubId", UUID.randomUUID().toString()).param("arrivalHubId", BUSAN_HUB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_ROUTE_NOT_FOUND"));
    }

    @Test
    @Transactional
    @DisplayName("I-03은 논리 삭제된 경로를 찾지 않는다")
    void internalExcludesDeleted() throws Exception {
        jdbcTemplate.update("UPDATE p_hub_routes SET deleted_at = NOW(), deleted_by = 'tester' WHERE hub_route_id = ?::uuid",
                routeId(SEOUL_HUB_ID, BUSAN_HUB_ID));

        mockMvc.perform(get("/internal/hub-routes").param("departureHubId", SEOUL_HUB_ID).param("arrivalHubId", BUSAN_HUB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_ROUTE_NOT_FOUND"));
    }

    private String routeId(String departureHubId, String arrivalHubId) {
        return jdbcTemplate.queryForObject(
                "SELECT hub_route_id::text FROM p_hub_routes WHERE departure_hub_id = ?::uuid AND arrival_hub_id = ?::uuid",
                String.class, departureHubId, arrivalHubId);
    }
}
