package com.sparta.logistics.user.domain;

import java.util.Optional;
import java.util.UUID;

/** 도메인 저장소 계약. 구현은 infrastructure/persistence (의존성 역전). 논리 삭제된 사용자는 조회되지 않는다. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID userId);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
