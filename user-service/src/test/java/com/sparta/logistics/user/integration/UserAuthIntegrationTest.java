package com.sparta.logistics.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.user.application.AuthService;
import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.application.dto.LoginCommand;
import com.sparta.logistics.user.application.dto.SignupCommand;
import com.sparta.logistics.user.application.dto.SignupResult;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"jwt.secret=test-only-jwt-secret-key-0123456789abcdef", "jwt.expiration-seconds=3600"})
class UserAuthIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Sparta!2026";

    @Autowired
    private AuthService authService;
    @Autowired
    private UserQueryService userQueryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;

    private static SignupCommand signup(String username) {
        return new SignupCommand(username, PASSWORD, "홍길동", "U01", AffiliationType.HUB, "서울특별시 센터");
    }

    @Test
    @DisplayName("회원가입하면 BCrypt 해시, USER 권한, Audit(SYSTEM)이 실제 DB에 저장된다")
    void signupPersists() {
        SignupResult result = authService.signup(signup("itsave01"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT password, role, is_delivery_manager, created_by, updated_by, created_at FROM p_users WHERE user_id = ?",
                result.userId());
        assertThat((String) row.get("password")).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("is_delivery_manager")).isEqualTo(false);
        assertThat(row.get("created_by")).isEqualTo("SYSTEM");
        assertThat(row.get("updated_by")).isEqualTo("SYSTEM");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("논리 삭제된 사용자는 로그인 실패, I-01 404이고, 같은 username으로 다시 가입할 수 없다")
    void withdrawnUser() {
        SignupResult result = authService.signup(signup("itdel01"));
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(result.userId()).orElseThrow().softDelete("master01"));

        assertThatThrownBy(() -> authService.login(new LoginCommand("itdel01", PASSWORD)))
                .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_FAILED);
        assertThatThrownBy(() -> userQueryService.getInternalUser(result.userId()))
                .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> authService.signup(signup("itdel01")))
                .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_USERNAME);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_by FROM p_users WHERE user_id = ?", String.class, result.userId()))
                .isEqualTo("master01");
    }

    @Test
    @DisplayName("같은 username으로 동시에 가입하면 하나만 성공하고 나머지는 500이 아니라 DUPLICATE_USERNAME")
    void concurrentSignup() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    return authService.signup(signup("itrace01"));
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            }));
        }
        start.countDown();

        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();

        assertThat(results).filteredOn(SignupResult.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(result -> !(result instanceof SignupResult))
                .hasSize(threads - 1)
                .containsOnly(UserErrorCode.DUPLICATE_USERNAME);
    }

    @Test
    @DisplayName("I-01 응답 필드명이 SA 6.3과 같다(isDeliveryManager 포함)")
    void internalUserContract() throws Exception {
        SignupResult result = authService.signup(signup("itapi01"));

        mockMvc.perform(get("/internal/users/{userId}", result.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(result.userId().toString()))
                .andExpect(jsonPath("$.data.username").value("itapi01"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.isDeliveryManager").value(false))
                .andExpect(jsonPath("$.data.hubId").doesNotExist())
                .andExpect(jsonPath("$.data.slackId").value("U01"));
    }
}
