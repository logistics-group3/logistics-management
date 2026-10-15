package com.sparta.logistics.delivery.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 배송(SA 5.6 p_deliveries). 주문 1건 = 배송 1건이고, 구간(DeliveryRoute)을 함께 관리하는 애그리거트 루트다.
 * Note: 구간 상태는 이 애그리거트를 통해서만 바꾼다. 그래야 구간 상태와 배송 상태가 한 트랜잭션 안에서 함께 바뀌어 어긋나지 않는다.
 */
@Getter
@Entity
@Table(name = "p_deliveries")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "delivery_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_slack_id", length = 50)
    private String recipientSlackId;

    /** order-service p_orders.order_id 논리 참조. I-09·I-13 멱등키 */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    /** 공급업체. company-service 논리 참조 */
    @Column(name = "supplier_company_id", nullable = false)
    private UUID supplierCompanyId;

    /** 요청업체. company-service 논리 참조 */
    @Column(name = "receiver_company_id", nullable = false)
    private UUID receiverCompanyId;

    /** 출발 허브 = 공급업체 관리 허브(생성 시점 값). hub-service 논리 참조 */
    @Column(name = "departure_hub_id", nullable = false)
    private UUID departureHubId;

    /** 목적지 허브 = 요청업체 관리 허브(생성 시점 값). hub-service 논리 참조 */
    @Column(name = "destination_hub_id", nullable = false)
    private UUID destinationHubId;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    private List<DeliveryRoute> routes = new ArrayList<>();
}
