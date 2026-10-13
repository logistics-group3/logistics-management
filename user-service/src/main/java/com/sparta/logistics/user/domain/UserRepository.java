package com.sparta.logistics.user.domain;

import java.util.Optional;
import java.util.UUID;

/** 도메인 계층의 저장소 인터페이스. 구현은 infrastructure.persistence.UserJpaRepository (Spring Data JPA) */
public interface UserRepository {

    <S extends User> S save(S user);

    <S extends User> S saveAndFlush(S user);

    /** 논리 삭제된 사용자는 조회되지 않는다 (@SQLRestriction) */
    Optional<User> findById(UUID userId);

    Optional<User> findByUsername(String username);

    /** 탈퇴(논리 삭제)한 사용자까지 포함해 username 사용 여부 확인 */
    boolean existsByUsernameIncludingDeleted(String username);
}
