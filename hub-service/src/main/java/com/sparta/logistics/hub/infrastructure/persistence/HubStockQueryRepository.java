package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.HubStock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** QueryDSL로 구현하는 재고 검색 조각. 이름이 {@code Impl}로 끝나는 구현 클래스를 Spring Data가 HubStockJpaRepository에 합친다. */
public interface HubStockQueryRepository {

    Page<HubStock> search(UUID hubId, UUID productId, Pageable pageable);
}
