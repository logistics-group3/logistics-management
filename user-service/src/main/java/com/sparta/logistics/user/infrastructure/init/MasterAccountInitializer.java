package com.sparta.logistics.user.infrastructure.init;

import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 회원가입은 항상 USER로 만들어지므로, 최초 MASTER는 기동 시 .env 값(MASTER_USERNAME/MASTER_PASSWORD)으로 만든다.
 * 이미 있거나 값이 비어 있으면 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${master.username:}")
    private String username;

    @Value("${master.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.info("MASTER_USERNAME/MASTER_PASSWORD가 없어 초기 MASTER 계정 생성을 건너뜁니다.");
            return;
        }
        if (userRepository.existsByUsername(username)) {
            return;
        }
        try {
            userRepository.save(User.master(username, passwordEncoder.encode(password)));
            log.info("초기 MASTER 계정을 생성했습니다: {}", username);
        } catch (DataIntegrityViolationException e) {
            // 인스턴스 여러 개가 동시에 기동하면 다른 인스턴스가 먼저 만들었을 수 있다.
            log.info("초기 MASTER 계정이 이미 생성되어 있습니다: {}", username);
        }
    }
}
