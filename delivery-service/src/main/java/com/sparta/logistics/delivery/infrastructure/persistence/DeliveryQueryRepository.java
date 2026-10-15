package com.sparta.logistics.delivery.infrastructure.persistence;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliverySearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** QueryDSL 검색 조각(Spring Data custom fragment). 구현은 이름 규칙대로 DeliveryQueryRepositoryImpl이다. */
public interface DeliveryQueryRepository {

    Page<Delivery> search(DeliverySearchCondition condition, Pageable pageable);
}
