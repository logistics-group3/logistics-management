package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** V2 시드 마이그레이션이 SA 10.1의 허브 17개를 올바르게 적재하는지 실제 PostgreSQL에서 확인한다. */
class HubSeedIntegrationTest extends PostgresIntegrationTest {

    // 대한민국 본토·제주를 포함하는 위경도 범위. 좌표가 뒤바뀌거나 자릿수가 틀린 값을 걸러낸다.
    private static final BigDecimal MIN_LAT = new BigDecimal("33.0");
    private static final BigDecimal MAX_LAT = new BigDecimal("38.7");
    private static final BigDecimal MIN_LON = new BigDecimal("124.5");
    private static final BigDecimal MAX_LON = new BigDecimal("131.9");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("허브 17개가 SA 10.1의 이름으로 적재된다")
    void seedsSeventeenHubs() {
        List<String> names = jdbcTemplate.queryForList("SELECT name FROM p_hubs WHERE deleted_at IS NULL", String.class);

        assertThat(names).containsExactlyInAnyOrder(
                "서울특별시 센터", "경기 북부 센터", "경기 남부 센터", "부산광역시 센터", "대구광역시 센터", "인천광역시 센터",
                "광주광역시 센터", "대전광역시 센터", "울산광역시 센터", "세종특별자치시 센터", "강원특별자치도 센터",
                "충청북도 센터", "충청남도 센터", "전북특별자치도 센터", "전라남도 센터", "경상북도 센터", "경상남도 센터");
    }

    @Test
    @DisplayName("모든 허브의 좌표가 한국 범위 안에 있고, 같은 좌표를 쓰는 허브가 없다")
    void coordinatesAreInKorea() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT name, latitude, longitude FROM p_hubs");

        assertThat(rows).allSatisfy(row -> {
            BigDecimal latitude = (BigDecimal) row.get("latitude");
            BigDecimal longitude = (BigDecimal) row.get("longitude");
            assertThat(latitude).as("%s 위도", row.get("name")).isBetween(MIN_LAT, MAX_LAT);
            assertThat(longitude).as("%s 경도", row.get("name")).isBetween(MIN_LON, MAX_LON);
        });
        assertThat(rows.stream().map(row -> row.get("latitude") + "," + row.get("longitude")).distinct()).hasSize(17);
    }

    @Test
    @DisplayName("시드 행의 Audit 작성자는 SYSTEM이다")
    void auditedBySystem() {
        Integer others = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM p_hubs WHERE created_by <> 'SYSTEM' OR updated_by <> 'SYSTEM'", Integer.class);

        assertThat(others).isZero();
    }
}
