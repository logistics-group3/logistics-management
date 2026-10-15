package com.sparta.logistics.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserRepository;
import com.sparta.logistics.user.infrastructure.init.MasterAccountInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "jwt.secret=test-only-jwt-secret-key-0123456789abcdef", "jwt.expiration-seconds=3600",
        "master.username=seedmstr", "master.password=Master!2026"})
class MasterAccountIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MasterAccountInitializer initializer;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기동하면 MASTER 계정이 실제 DB에 만들어지고, 다시 실행해도 하나만 남는다")
    void createsMasterOnceOnStartup() {
        User master = userRepository.findByUsername("seedmstr").orElseThrow();
        assertThat(master.getRole()).isEqualTo(Role.MASTER);
        assertThat(passwordEncoder.matches("Master!2026", master.getPassword())).isTrue();
        assertThat(master.getCreatedBy()).isEqualTo("SYSTEM");

        initializer.run(null);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM p_users WHERE username = 'seedmstr'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
