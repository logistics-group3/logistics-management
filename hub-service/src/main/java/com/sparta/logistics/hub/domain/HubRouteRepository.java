package com.sparta.logistics.hub.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HubRouteRepository {

    Optional<HubRoute> findById(UUID hubRouteId);

    Page<HubRoute> search(UUID departureHubId, UUID arrivalHubId, Pageable pageable);
}
