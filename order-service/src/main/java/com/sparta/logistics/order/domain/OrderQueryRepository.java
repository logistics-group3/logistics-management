package com.sparta.logistics.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** O-02 동적 검색 계약. QueryDSL 구현과 Q타입은 infrastructure에 둔다. */
public interface OrderQueryRepository {

    Page<Order> search(OrderSearchCriteria criteria, Pageable pageable);
}
