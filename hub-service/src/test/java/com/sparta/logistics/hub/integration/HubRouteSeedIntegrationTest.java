package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V3 시드 마이그레이션이 SA 10.1의 경로 272건을 올바르게 적재하는지 실제 PostgreSQL에서 확인한다.
 * Note: 자기 경로·양수 값·중복 방향은 V1의 CHECK 제약과 부분 유니크 인덱스가 이미 막으므로 다시 검사하지 않는다.
 */
class HubRouteSeedIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("허브 17개의 모든 방향 경로 272건이 적재되고 모든 허브가 출발 16건, 도착 16건을 가진다")
    void seedsAllOrderedPairs() {
        Integer routes = count("SELECT COUNT(*) FROM p_hub_routes WHERE deleted_at IS NULL");
        Integer hubsWithAllRoutes = count("""
                SELECT COUNT(*) FROM p_hubs h
                WHERE (SELECT COUNT(*) FROM p_hub_routes WHERE departure_hub_id = h.hub_id) = 16
                  AND (SELECT COUNT(*) FROM p_hub_routes WHERE arrival_hub_id = h.hub_id) = 16
                """);

        assertThat(routes).isEqualTo(272);
        assertThat(hubsWithAllRoutes).isEqualTo(17);
    }

    @Test
    @DisplayName("저장된 거리는 Haversine × 1.3을 SQL로 다시 계산한 값과 0.01km 안에서 같고, 시간은 거리 ÷ 70km/h를 올림한 값이다")
    void valuesFollowSaFormula() {
        // Note: PostgreSQL과 Java의 부동소수 오차로 반올림 경계가 한 칸 갈릴 수 있어 거리는 0.01km 허용 오차로 대조한다.
        Integer wrongDistance = count("""
                SELECT COUNT(*) FROM p_hub_routes r
                JOIN p_hubs d ON d.hub_id = r.departure_hub_id
                JOIN p_hubs a ON a.hub_id = r.arrival_hub_id
                WHERE abs(r.distance_km - round((2 * 6371 * asin(sqrt(
                        power(sin(radians(a.latitude - d.latitude) / 2), 2)
                        + cos(radians(d.latitude)) * cos(radians(a.latitude))
                        * power(sin(radians(a.longitude - d.longitude) / 2), 2)))
                        * 1.3)::numeric, 2)) > 0.01
                """);
        Integer wrongDuration = count(
                "SELECT COUNT(*) FROM p_hub_routes WHERE duration_minutes <> ceil(distance_km * 60 / 70)");

        assertThat(wrongDistance).isZero();
        assertThat(wrongDuration).isZero();
    }

    @Test
    @DisplayName("출발과 도착을 바꾼 두 경로의 거리와 시간은 같다")
    void reverseRoutesAreSymmetric() {
        Integer asymmetric = count("""
                SELECT COUNT(*) FROM p_hub_routes r
                JOIN p_hub_routes back ON back.departure_hub_id = r.arrival_hub_id AND back.arrival_hub_id = r.departure_hub_id
                WHERE r.distance_km <> back.distance_km OR r.duration_minutes <> back.duration_minutes
                """);

        assertThat(asymmetric).isZero();
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
