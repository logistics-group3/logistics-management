package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.HubStockHistory;
import com.sparta.logistics.hub.domain.HubStockHistoryRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubStockHistoryJpaRepository extends JpaRepository<HubStockHistory, UUID>, HubStockHistoryRepository {
}
