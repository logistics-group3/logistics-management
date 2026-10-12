package com.sparta.logistics.user.domain.repository;

import com.sparta.logistics.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    /** 삭제된 사용자는 조회되지 않는다 (@SQLRestriction) */
    Optional<User> findById(UUID userId);

    Optional<User> findByUsername(String username);

    /** 탈퇴(논리 삭제)한 사용자의 username까지 포함해 중복 확인 */
    boolean existsByUsernameIncludingDeleted(String username);
}
