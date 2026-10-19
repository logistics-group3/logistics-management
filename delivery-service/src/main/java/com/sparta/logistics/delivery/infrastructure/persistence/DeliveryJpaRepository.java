package com.sparta.logistics.delivery.infrastructure.persistence;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** DeliveryRepository 구현. Spring Data JPA가 메서드 이름과 @Query로 구현을 만든다(연결용 구현 클래스를 두지 않는다). */
public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID>, DeliveryRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.orderId = :orderId")
    Optional<Delivery> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
