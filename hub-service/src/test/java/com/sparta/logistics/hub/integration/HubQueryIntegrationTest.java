package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.infrastructure.persistence.HubJpaRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 Flyway 스키마, QueryDSL 검색·정렬·페이징, 논리 삭제 제외를 HTTP까지 확인한다. */
// Note: 컨테이너 DB를 다른 통합 테스트(시드 검증)와 공유하므로 클래스 전체를 트랜잭션으로 묶어 테스트가 끝나면 롤백한다. MockMvc는 같은 스레드라 미커밋 데이터를 본다.
@Transactional
@AutoConfigureMockMvc
class HubQueryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HubJpaRepository hubRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Hub seoul;
    private Hub busan;

    @BeforeEach
    void setUp() {
        // Note: deleteAll()은 @SQLRestriction 때문에 논리 삭제된 행을 지우지 못해 다음 테스트에서 이름 유니크 제약에 걸린다. SQL로 직접 비운다.
        // 기동 시 적재된 경로 272건이 허브를 FK로 참조하므로 경로부터 비운다(클래스 트랜잭션이라 롤백된다).
        jdbcTemplate.update("DELETE FROM p_hub_routes");
        jdbcTemplate.update("DELETE FROM p_hubs");
        seoul = hubRepository.save(HubFixture.hub("서울특별시 센터", "서울특별시 송파구 송파대로 55"));
        busan = hubRepository.save(HubFixture.hub("부산광역시 센터", "부산 동구 중앙대로 206"));
        hubRepository.save(HubFixture.hub("경기 남부 센터", "경기도 이천시 덕평로 257-21"));
        Hub removed = hubRepository.save(HubFixture.hub("폐쇄 센터", "서울특별시 중구 폐쇄로 1"));
        removed.softDelete("tester");
        hubRepository.save(removed);
    }

    @Test
    @DisplayName("keyword 없이 조회하면 논리 삭제된 허브를 뺀 전체가 나온다")
    void listExcludesDeleted() throws Exception {
        mockMvc.perform(get("/api/v1/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.sort").value("createdAt,DESC"));
    }

    @Test
    @DisplayName("keyword는 이름 또는 주소에 부분 일치하고 삭제된 허브는 검색되지 않는다")
    void keywordMatchesNameOrAddress() throws Exception {
        mockMvc.perform(get("/api/v1/hubs").param("keyword", "서울"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("서울특별시 센터"));
        mockMvc.perform(get("/api/v1/hubs").param("keyword", "이천"))
                .andExpect(jsonPath("$.data.content[0].name").value("경기 남부 센터"));
    }

    @Test
    @DisplayName("결과가 없으면 404가 아니라 빈 페이지")
    void emptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/hubs").param("keyword", "존재하지않음"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("퍼센트 기호는 와일드카드가 아니라 문자 그대로 검색한다")
    void percentIsLiteral() throws Exception {
        mockMvc.perform(get("/api/v1/hubs").param("keyword", "%"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("name 정렬은 오름차순과 내림차순이 서로 역순이고, 범위를 벗어난 페이지는 비어 있다")
    void sortAndPaging() throws Exception {
        // Note: 문자열 정렬 순서는 DB 콜레이션이 정한다(컨테이너 PostgreSQL에서 한글이 가나다 순이 아니었다). 순서 자체가 아니라 방향이 뒤집히는지를 검증한다.
        List<String> asc = names(get("/api/v1/hubs").param("sort", "name,asc").param("size", "10"));
        List<String> desc = names(get("/api/v1/hubs").param("sort", "name,desc").param("size", "10"));

        assertThat(asc).hasSize(3).containsExactlyInAnyOrder("서울특별시 센터", "부산광역시 센터", "경기 남부 센터");
        assertThat(desc).containsExactlyElementsOf(reversed(asc));

        mockMvc.perform(get("/api/v1/hubs").param("sort", "name,asc").param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @DisplayName("페이지를 나눠 조회해도 같은 정렬 기준에서 항목이 겹치거나 빠지지 않는다")
    void pagesDoNotOverlap() throws Exception {
        for (int i = 0; i < 12; i++) {
            hubRepository.save(HubFixture.hub("테스트 센터 " + i, "테스트 주소 " + i));
        }

        List<String> first = names(get("/api/v1/hubs").param("sort", "createdAt,asc").param("page", "0").param("size", "10"));
        List<String> second = names(get("/api/v1/hubs").param("sort", "createdAt,asc").param("page", "1").param("size", "10"));

        assertThat(first).hasSize(10);
        assertThat(second).hasSize(5);
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    private List<String> names(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.content[*].name");
    }

    private static List<String> reversed(List<String> list) {
        List<String> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    @Test
    @DisplayName("허용 목록에 없는 정렬 필드는 400 VALIDATION_ERROR")
    void disallowedSort() throws Exception {
        mockMvc.perform(get("/api/v1/hubs").param("sort", "latitude,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("H-02는 좌표까지 반환하고, 없는 허브는 404 HUB_NOT_FOUND")
    void getHub() throws Exception {
        mockMvc.perform(get("/api/v1/hubs/{hubId}", seoul.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.latitude").value(37.5665));
        mockMvc.perform(get("/api/v1/hubs/{hubId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"));
    }

    @Test
    @DisplayName("I-02는 감싸지 않은 본문으로 허브 ID·이름·주소를 반환한다")
    void internalGetHub() throws Exception {
        mockMvc.perform(get("/internal/hubs/{hubId}", busan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubId").value(busan.getId().toString()))
                .andExpect(jsonPath("$.name").value("부산광역시 센터"))
                .andExpect(jsonPath("$.address").value("부산 동구 중앙대로 206"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
