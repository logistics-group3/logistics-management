package com.sparta.logistics.delivery.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 배송 애그리거트 저장소. 구간은 배송을 통해서만 저장·조회한다. 구현은 infrastructure/persistence. */
public interface DeliveryRepository {

    /** 배송과 구간을 저장하고 바로 INSERT한다. order_id 유니크 위반을 호출한 곳에서 잡기 위해 쓴다. */
    Delivery saveAndFlush(Delivery delivery);

    Optional<Delivery> findById(UUID id);

    Optional<Delivery> findByOrderId(UUID orderId);

    Page<Delivery> search(DeliverySearchCondition condition, Pageable pageable);

    /** 후보별 담당 건수(WAITING·MOVING 구간 수, 논리 삭제 제외). 담당 구간이 없는 후보는 결과에 없다. */
    List<ActiveRouteCount> countActiveRoutes(Collection<UUID> managerIds);
}
