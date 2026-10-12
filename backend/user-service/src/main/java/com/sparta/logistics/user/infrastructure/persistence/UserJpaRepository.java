package com.sparta.logistics.user.infrastructure.persistence;

import com.sparta.logistics.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    /** @SQLRestriction을 우회해 삭제된 행까지 확인 (username 재사용 금지) */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM p_users WHERE username = :username)", nativeQuery = true)
    boolean existsByUsernameIncludingDeleted(@Param("username") String username);
}
