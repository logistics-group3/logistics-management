package com.sparta.logistics.common.security;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;

/** 감사 컬럼(created_by 등)의 값. X-Username 헤더, 없으면 SYSTEM. */
public class HeaderAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(UserContextHolder.currentUsernameOrSystem());
    }
}
