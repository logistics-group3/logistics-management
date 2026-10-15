package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubJpaRepository extends JpaRepository<Hub, UUID>, HubRepository, HubQueryRepository {
}
