package com.sparta.logistics.user.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.user.application.AuthService;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** 조회 API(U-03, U-04)를 실제 DB로 확인한다. UserAuthIntegrationTest와 설정이 같아 Spring 컨텍스트를 재사용한다. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"jwt.secret=test-only-jwt-secret-key-0123456789abcdef", "jwt.expiration-seconds=3600"})
class UserQueryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("U-03은 실제 DB의 Audit 시각을 담아 응답하고, 탈퇴한 사용자는 MASTER가 U-04로 조회해도 404다")
    void userReadApis() throws Exception {
        UUID userId = signup("itread01");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(UserHeaders.USER_ID, userId.toString())
                        .header(UserHeaders.USERNAME, "itread01")
                        .header(UserHeaders.ROLE, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("itread01"))
                .andExpect(jsonPath("$.data.affiliationName").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        softDelete(userId);

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .header(UserHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(UserHeaders.USERNAME, "master01")
                        .header(UserHeaders.ROLE, "MASTER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private UUID signup(String username) {
        return signup(username, "홍길동");
    }

    private UUID signup(String username, String name) {
        return authService.signup(new SignupCommand(username, "Sparta!2026", name, "U01", AffiliationType.HUB, "서울특별시 센터"))
                .userId();
    }

    private void softDelete(UUID userId) {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(userId).orElseThrow().softDelete("master01"));
    }
}
