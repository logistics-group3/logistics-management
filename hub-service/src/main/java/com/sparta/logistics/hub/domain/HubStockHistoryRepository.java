package com.sparta.logistics.hub.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubStockHistoryRepository {

    HubStockHistory save(HubStockHistory history);

    /** 한 주문의 특정 유형 이력(예: 차감 이력). 허브 재고마다 최대 1건이다. */
    List<HubStockHistory> findAllByOrderIdAndChangeType(UUID orderId, StockChangeType changeType);

    /** 한 허브 재고에 대한 주문의 이력. (hub_stock_id, order_id, change_type) 유니크 인덱스로 찾는다. */
    Optional<HubStockHistory> findByHubStockIdAndOrderIdAndChangeType(UUID hubStockId, UUID orderId, StockChangeType changeType);

    boolean existsByHubStockIdAndOrderIdAndChangeType(UUID hubStockId, UUID orderId, StockChangeType changeType);
}
