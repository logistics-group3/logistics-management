package com.sparta.logistics.user.infrastructure.init;

import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초기 MASTER 계정 생성. 회원가입은 항상 USER 로만 만들어지므로, 403 테스트와 관리자 기능 개발용 MASTER 가 하나 필요하다.
 * 이미 같은 username 이 있으면 아무것도 하지 않는다. (app.seed.master.enabled=false 로 끌 수 있음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed.master", name = "enabled", havingValue = "true")
public class MasterAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.master.username}")
    private String username;

    @Value("${app.seed.master.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIncludingDeleted(username)) {
            return;
        }
        userRepository.save(User.master(username, passwordEncoder.encode(password), "마스터", "U00MASTER00"));
        log.info("Initial MASTER account created: {}", username);
    }
}
