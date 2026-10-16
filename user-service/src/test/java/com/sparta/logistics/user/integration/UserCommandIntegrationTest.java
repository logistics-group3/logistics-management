package com.sparta.logistics.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.user.application.AuthService;
import com.sparta.logistics.user.application.HubReader;
import com.sparta.logistics.user.application.dto.HubInfo;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.domain.AffiliationType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 변경 API(U-06~U-08)를 실제 DB로 확인한다. hub-service는 아직 없으므로 HubReader만 목으로 바꾼다.
 * 목 빈이 있어 조회 테스트와 Spring 컨텍스트가 따로 뜬다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"jwt.secret=test-only-jwt-secret-key-0123456789abcdef", "jwt.expiration-seconds=3600"})
class UserCommandIntegrationTest extends PostgresIntegrationTest {

    private static final String MASTER = "master01";

    @Autowired
    private AuthService authService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubReader hubReader;

    @Test
    @DisplayName("U-06 수정은 보낸 필드만 DB에 반영하고 updated_by에 요청한 MASTER를 남긴다")
    void updateProfile() throws Exception {
        UUID userId = signup("itupd01");

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}", userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slackId\":\"U09ZYXWV8UT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slackId").value("U09ZYXWV8UT"));

        Map<String, Object> row = row(userId);
        assertThat(row.get("slack_id")).isEqualTo("U09ZYXWV8UT");
        assertThat(row.get("name")).isEqualTo("홍길동");
        assertThat(row.get("updated_by")).isEqualTo(MASTER);
    }

    @Test
    @DisplayName("U-07 배송담당자로 지정하면 허브와 소속이 저장되고, 이후 U-06으로 소속을 바꾸면 INVALID_REQUEST")
    void assignDeliveryManagerThenAffiliationIsFixed() throws Exception {
        UUID userId = signup("itrole01");
        UUID hubId = UUID.randomUUID();
        given(hubReader.getHub(hubId)).willReturn(new HubInfo(hubId, "경기 남부 센터"));

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}/role", userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isDeliveryManager\":true,\"hubId\":\"" + hubId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDeliveryManager").value(true))
                .andExpect(jsonPath("$.data.affiliationName").value("경기 남부 센터"));

        Map<String, Object> row = row(userId);
        assertThat(row.get("hub_id")).isEqualTo(hubId);
        assertThat(row.get("affiliation_type")).isEqualTo("HUB");

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}", userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"affiliationType\":\"COMPANY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("U-07 해제하면 hub_id가 NULL이 되고, 권한 변경도 DB에 반영된다")
    void releaseAndPromote() throws Exception {
        UUID userId = signup("itrole02");
        UUID hubId = UUID.randomUUID();
        given(hubReader.getHub(hubId)).willReturn(new HubInfo(hubId, "서울특별시 센터"));
        changeRole(userId, "{\"isDeliveryManager\":true,\"hubId\":\"" + hubId + "\"}");

        changeRole(userId, "{\"role\":\"MASTER\",\"isDeliveryManager\":false}");

        Map<String, Object> row = row(userId);
        assertThat(row.get("is_delivery_manager")).isEqualTo(false);
        assertThat(row.get("hub_id")).isNull();
        assertThat(row.get("role")).isEqualTo("MASTER");
    }

    @Test
    @DisplayName("U-08 삭제하면 deleted_by에 MASTER가 남고, 이후 I-01은 404다")
    void deleteUser() throws Exception {
        UUID userId = signup("itdelapi1");

        mockMvc.perform(asMaster(delete("/api/v1/users/{userId}", userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사용자가 삭제되었습니다."));

        assertThat(row(userId).get("deleted_by")).isEqualTo(MASTER);
        mockMvc.perform(get("/internal/users/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private void changeRole(UUID userId, String body) throws Exception {
        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}/role", userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder asMaster(MockHttpServletRequestBuilder request) {
        return request.header(UserHeaders.USER_ID, UUID.randomUUID().toString())
                .header(UserHeaders.USERNAME, MASTER)
                .header(UserHeaders.ROLE, "MASTER");
    }

    private UUID signup(String username) {
        return authService.signup(new SignupCommand(username, "Sparta!2026", "홍길동", AffiliationType.COMPANY, "부산수산도매", "company@example.com"))
                .userId();
    }

    private Map<String, Object> row(UUID userId) {
        return jdbcTemplate.queryForMap("SELECT * FROM p_users WHERE user_id = ?", userId);
    }
}
