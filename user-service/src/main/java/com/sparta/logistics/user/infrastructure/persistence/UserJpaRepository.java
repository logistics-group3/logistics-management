package com.sparta.logistics.user.infrastructure.persistence;

import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<User, UUID>, UserRepository {

    /** @SQLRestriction 을 우회해야 하므로 native query 사용 */
    @Override
    @Query(value = "SELECT EXISTS (SELECT 1 FROM p_users WHERE username = :username)", nativeQuery = true)
    boolean existsByUsernameIncludingDeleted(@Param("username") String username);
}
