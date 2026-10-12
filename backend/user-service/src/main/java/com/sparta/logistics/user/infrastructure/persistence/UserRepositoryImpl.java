package com.sparta.logistics.user.infrastructure.persistence;

import com.sparta.logistics.user.domain.model.User;
import com.sparta.logistics.user.domain.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
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
    public boolean existsByUsernameIncludingDeleted(String username) {
        return jpaRepository.existsByUsernameIncludingDeleted(username);
    }
}
