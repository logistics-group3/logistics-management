package com.sparta.logistics.delivery.infrastructure.persistence;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** DeliveryRepository 구현. Spring Data JPA가 메서드 이름으로 구현을 만든다(연결용 구현 클래스를 두지 않는다). */
public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID>, DeliveryRepository {
}
