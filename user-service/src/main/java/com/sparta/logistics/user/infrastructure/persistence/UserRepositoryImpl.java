package com.sparta.logistics.user.infrastructure.persistence;

import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    /** 유니크 위반을 호출한 트랜잭션 안에서 바로 감지하도록 flush 한다. */
    @Override
    public User save(User user) {
        return jpaRepository.saveAndFlush(user);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return jpaRepository.findById(userId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }
}
