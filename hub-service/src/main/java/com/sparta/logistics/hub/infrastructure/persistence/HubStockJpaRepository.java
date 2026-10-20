package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HubStockJpaRepository extends JpaRepository<HubStock, UUID>, HubStockRepository, HubStockQueryRepository {

    // Note: 입고·할당은 읽은 수량에 더하거나 빼서 저장하므로 잠그지 않으면 동시 요청이 서로의 변경을 덮어쓴다(갱신 손실).
    // 허브·상품당 재고 행은 하나라 이 행만 잠그면 되고, 잠그는 행이 하나뿐이라 교착이 생기지 않는다. 반드시 트랜잭션 안에서 호출해야 락이 유지된다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HubStock s where s.hubId = :hubId and s.productId = :productId")
    Optional<HubStock> lockByHubIdAndProductId(@Param("hubId") UUID hubId, @Param("productId") UUID productId);

    // 복원도 할당과 같은 행을 잠가 같은 재고의 할당·복원이 순서대로 처리되게 한다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HubStock s where s.id = :stockId")
    Optional<HubStock> lockById(@Param("stockId") UUID stockId);
}
