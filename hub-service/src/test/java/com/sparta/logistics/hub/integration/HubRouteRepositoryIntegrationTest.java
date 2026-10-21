package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.InvalidSortException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubRoute;
import com.sparta.logistics.hub.infrastructure.persistence.HubJpaRepository;
import com.sparta.logistics.hub.infrastructure.persistence.HubRouteJpaRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 경로 검색 필터·정렬·페이징, 논리 삭제 제외, 허브 fetch join(N+1 없음)을 확인한다. */
// Note: 컨테이너 DB를 다른 통합 테스트와 공유하므로 클래스 전체를 트랜잭션으로 묶어 테스트가 끝나면 롤백한다.
@Transactional
class HubRouteRepositoryIntegrationTest extends PostgresIntegrationTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "distanceKm"));

    @Autowired
    private HubRouteJpaRepository hubRouteRepository;
    @Autowired
    private HubJpaRepository hubRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private Hub seoul;
    private Hub busan;
    private Hub daegu;
    private HubRoute seoulToBusan;
    private HubRoute seoulToDaegu;
    private HubRoute busanToSeoul;

    @BeforeEach
    void setUp() {
        // Note: 경로가 허브를 FK로 참조하므로 경로부터 비운다. 시드 허브가 있어도 이 테스트가 만든 허브만 쓰도록 SQL로 직접 비운다.
        jdbcTemplate.update("DELETE FROM p_hub_routes");
        jdbcTemplate.update("DELETE FROM p_hubs");
        seoul = hubRepository.save(HubFixture.hub("서울특별시 센터", "서울특별시 송파구 송파대로 55"));
        busan = hubRepository.save(HubFixture.hub("부산광역시 센터", "부산 동구 중앙대로 206"));
        daegu = hubRepository.save(HubFixture.hub("대구광역시 센터", "대구 북구 태평로 161"));
        seoulToBusan = route(seoul, busan, "325.00", 280);
        seoulToDaegu = route(seoul, daegu, "237.50", 204);
        busanToSeoul = route(busan, seoul, "325.00", 280);
        clear();
    }

    @Test
    @DisplayName("필터 없이 조회하면 논리 삭제된 경로를 뺀 전체가 나온다")
    void listExcludesDeleted() {
        HubRoute removed = route(daegu, busan, "100.00", 86);
        removed.softDelete("tester");
        hubRouteRepository.saveAndFlush(removed);
        clear();

        Page<HubRoute> page = hubRouteRepository.search(null, null, FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(HubRoute::getId)
                .containsExactlyInAnyOrder(seoulToBusan.getId(), seoulToDaegu.getId(), busanToSeoul.getId());
    }

    @Test
    @DisplayName("출발 허브만, 도착 허브만, 둘 다 지정한 조합이 각각 걸러진다")
    void filterCombinations() {
        assertThat(ids(hubRouteRepository.search(seoul.getId(), null, FIRST_PAGE)))
                .containsExactlyInAnyOrder(seoulToBusan.getId(), seoulToDaegu.getId());
        assertThat(ids(hubRouteRepository.search(null, seoul.getId(), FIRST_PAGE)))
                .containsExactly(busanToSeoul.getId());
        assertThat(ids(hubRouteRepository.search(seoul.getId(), busan.getId(), FIRST_PAGE)))
                .containsExactly(seoulToBusan.getId());
    }

    @Test
    @DisplayName("방향이 반대인 경로는 별개이며, 없는 조합은 빈 페이지다")
    void directionAndEmpty() {
        assertThat(hubRouteRepository.search(daegu.getId(), seoul.getId(), FIRST_PAGE).getContent()).isEmpty();
        assertThat(hubRouteRepository.search(UUID.randomUUID(), null, FIRST_PAGE).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("distanceKm·durationMinutes 정렬은 방향을 따르고, 값이 같으면 PK 순으로 안정적이다")
    void sortsByAllowedFields() {
        Page<HubRoute> asc = hubRouteRepository.search(null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "distanceKm")));
        assertThat(asc.getContent().get(0).getId()).isEqualTo(seoulToDaegu.getId());

        Page<HubRoute> desc = hubRouteRepository.search(null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "durationMinutes")));
        assertThat(desc.getContent().get(2).getId()).isEqualTo(seoulToDaegu.getId());

        Pageable byDistance = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "distanceKm"));
        UUID first = hubRouteRepository.search(null, null, byDistance).getContent().get(0).getId();
        UUID second = hubRouteRepository.search(null, null, byDistance.next()).getContent().get(0).getId();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("허용하지 않은 정렬 필드는 InvalidSortException")
    void rejectsUnknownSort() {
        Pageable invalid = PageRequest.of(0, 10, Sort.by("departureHub.name"));

        assertThatThrownBy(() -> hubRouteRepository.search(null, null, invalid)).isInstanceOf(InvalidSortException.class);
    }

    @Test
    @DisplayName("목록 조회 후 허브명을 읽어도 쿼리가 추가로 나가지 않는다(N+1 없음)")
    void listFetchesHubsWithOneSelect() {
        Statistics statistics = statistics();

        Page<HubRoute> page = hubRouteRepository.search(null, null, FIRST_PAGE);
        page.getContent().forEach(r -> r.getDepartureHub().getName());
        page.getContent().forEach(r -> r.getArrivalHub().getName());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("단건 조회는 허브를 함께 읽고, 논리 삭제된 경로는 조회되지 않는다")
    void findByIdFetchesHubsAndExcludesDeleted() {
        Statistics statistics = statistics();

        HubRoute found = hubRouteRepository.findById(seoulToBusan.getId()).orElseThrow();
        assertThat(found.getDepartureHub().getName()).isEqualTo("서울특별시 센터");
        assertThat(found.getArrivalHub().getName()).isEqualTo("부산광역시 센터");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        found.softDelete("tester");
        hubRouteRepository.saveAndFlush(found);
        clear();
        assertThat(hubRouteRepository.findById(seoulToBusan.getId())).isEmpty();
    }

    private HubRoute route(Hub from, Hub to, String distanceKm, int durationMinutes) {
        return hubRouteRepository.saveAndFlush(HubRoute.create(from, to, new BigDecimal(distanceKm), durationMinutes));
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    private static List<UUID> ids(Page<HubRoute> page) {
        return page.getContent().stream().map(HubRoute::getId).toList();
    }
}
