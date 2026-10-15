package com.sparta.logistics.user.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"jwt.secret=test-only-jwt-secret-key-0123456789abcdef", "jwt.expiration-seconds=3600", "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
class SlackEmailIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("신규 가입은 Slack 멤버 ID 없이 이메일만 저장한다")
    void signupWithEmailOnly() throws Exception {
        var body = Map.of("username", "mailonly01", "password", "Sparta!2026", "name", "이메일 가입",
                "slackEmail", "mailonly@example.com", "affiliationType", "COMPANY", "affiliationName", "테스트 업체");
        String response = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(response).path("data").path("userId").asText());
        assertThat(email(id)).isEqualTo("mailonly@example.com");
        assertThat(jdbc.queryForObject("SELECT slack_id FROM p_users WHERE user_id=?", String.class, id)).isNull();
        mvc.perform(get("/internal/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slackId").isEmpty());
    }

    @Test
    @DisplayName("가입 이메일은 전용 연락처 API에만 제공하고 기존 내부 응답에는 추가하지 않는다")
    void signupAndContact() throws Exception {
        UUID id = signup("dmnew01", "driver+team@example.com");
        assertThat(email(id)).isEqualTo("driver+team@example.com");
        mvc.perform(get("/internal/users/{id}/notification-contact", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slackEmail").value("driver+team@example.com"))
                .andExpect(jsonPath("$.isDeliveryManager").value(false)).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/internal/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slackEmail").doesNotExist());
    }

    @Test
    @DisplayName("MASTER는 이메일을 수정·해제하고 생략은 기존 값 유지, 일반 USER 수정은 거절한다")
    void patchSemanticsAndPermission() throws Exception {
        UUID id = signup("dmpatch01", "before@example.com");
        mvc.perform(headers(patch("/api/v1/users/{id}", id), "USER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slackEmail\":\"other@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(headers(patch("/api/v1/users/{id}", id), "MASTER").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.slackEmail").value("before@example.com"));
        mvc.perform(headers(patch("/api/v1/users/{id}", id), "MASTER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slackEmail\":\"after@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.slackEmail").value("after@example.com"));
        mvc.perform(headers(patch("/api/v1/users/{id}", id), "MASTER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slackEmail\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.slackEmail").doesNotExist());
        assertThat(email(id)).isNull();
    }

    @Test
    @DisplayName("가입 이메일을 생략하거나 비우면 멤버 ID가 있어도 가입할 수 없다")
    void signupRequiresEmail() throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("username", "nemail01", "password", "Sparta!2026",
                "name", "필수 이메일 검증", "slackId", "U_LEGACY", "affiliationType", "HUB", "affiliationName", "서울"));
        for (String email : new String[]{null, "", "   ", "invalid-email"}) {
            if (email == null) body.remove("slackEmail"); else body.put("slackEmail", email);
            mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_users WHERE username='nemail01'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("잘못된 이메일과 DB 정규화 제약 위반을 거절한다")
    void validationAndDatabaseConstraint() throws Exception {
        UUID id = signup("dmbad01", "before@example.com");
        mvc.perform(headers(patch("/api/v1/users/{id}", id), "MASTER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slackEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThatThrownBy(() -> jdbc.update("UPDATE p_users SET slack_email=' ' WHERE user_id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(email(id)).isEqualTo("before@example.com");
    }

    @Test
    @DisplayName("탈퇴 사용자의 알림 연락처는 조회되지 않는다")
    void excludesDeletedContact() throws Exception {
        UUID id = signup("dmdel01", "deleted@example.com");
        mvc.perform(headers(delete("/api/v1/users/{id}", id), "MASTER")).andExpect(status().isOk());
        mvc.perform(get("/internal/users/{id}/notification-contact", id)).andExpect(status().isNotFound());
    }

    private UUID signup(String username, String email) throws Exception {
        var values = new java.util.HashMap<String,Object>(Map.of("username", username, "password", "Sparta!2026", "name", "DM 담당자", "affiliationType", "HUB", "affiliationName", "서울"));
        if (email != null) values.put("slackEmail", email);
        String response = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(values)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).path("data").path("userId").asText());
    }
    private String email(UUID id) { return jdbc.queryForObject("SELECT slack_email FROM p_users WHERE user_id=?", String.class, id); }
    private MockHttpServletRequestBuilder headers(MockHttpServletRequestBuilder request, String role) {
        return request.header(UserHeaders.USER_ID, UUID.randomUUID()).header(UserHeaders.USERNAME, "dmadmin").header(UserHeaders.ROLE, role).header(UserHeaders.DELIVERY_MANAGER, "false");
    }
}
