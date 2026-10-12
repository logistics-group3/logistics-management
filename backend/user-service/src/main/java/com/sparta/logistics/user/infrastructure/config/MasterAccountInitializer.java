package com.sparta.logistics.user.infrastructure.config;

import com.sparta.logistics.common.security.UserRole;
import com.sparta.logistics.user.domain.model.AffiliationType;
import com.sparta.logistics.user.domain.model.User;
import com.sparta.logistics.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 MASTER 계정 생성. 회원가입은 USER로만 되므로 권한을 부여할 MASTER가 한 명 있어야 한다.
 * 계정 정보: MASTER_USERNAME / MASTER_PASSWORD 환경 변수. 이미 있으면(탈퇴 포함) 만들지 않는다.
 * created_by는 요청 헤더가 없으므로 SYSTEM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.master.username}")
    private String username;

    @Value("${app.master.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIncludingDeleted(username)) {
            return;
        }
        userRepository.save(User.create(
                username,
                passwordEncoder.encode(password),
                "관리자",
                "MASTER",
                AffiliationType.HUB,
                "스파르타 물류 본사",
                UserRole.MASTER
        ));
        log.info("최초 MASTER 계정을 생성했습니다. username={}", username);
    }
}
