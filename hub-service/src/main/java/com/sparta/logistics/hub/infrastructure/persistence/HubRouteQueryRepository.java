package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.HubRoute;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HubRouteQueryRepository {

    Page<HubRoute> search(UUID departureHubId, UUID arrivalHubId, Pageable pageable);
}
