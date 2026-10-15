package com.sparta.logistics.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.user.application.AuthService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 변경 API(U-06, U-08)를 실제 DB로 확인한다. */
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

    private MockHttpServletRequestBuilder asMaster(MockHttpServletRequestBuilder request) {
        return request.header(UserHeaders.USER_ID, UUID.randomUUID().toString())
                .header(UserHeaders.USERNAME, MASTER)
                .header(UserHeaders.ROLE, "MASTER");
    }

    private UUID signup(String username) {
        return authService.signup(new SignupCommand(username, "Sparta!2026", "홍길동", "U01", AffiliationType.COMPANY, "부산수산도매"))
                .userId();
    }

    private Map<String, Object> row(UUID userId) {
        return jdbcTemplate.queryForMap("SELECT * FROM p_users WHERE user_id = ?", userId);
    }
}
