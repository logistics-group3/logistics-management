package com.sparta.logistics.hub.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** domain이 필요로 하는 재고 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다. */
public interface HubStockRepository {

    HubStock saveAndFlush(HubStock stock);

    Optional<HubStock> findByHubIdAndProductId(UUID hubId, UUID productId);

    /** 허브의 재고 목록. productId가 null이면 전체. 정렬은 Pageable을 따른다(createdAt, quantity). */
    Page<HubStock> search(UUID hubId, UUID productId, Pageable pageable);

    /** 읽은 값에 더하거나 빼서 저장하는 흐름(입고, 할당)에서 갱신 손실을 막으려고 행을 잠가 가져온다. 트랜잭션 안에서만 호출한다. */
    Optional<HubStock> lockByHubIdAndProductId(UUID hubId, UUID productId);

    /** 재고 ID로 행을 잠가 가져온다(복원). 트랜잭션 안에서만 호출한다. */
    Optional<HubStock> lockById(UUID stockId);
}
