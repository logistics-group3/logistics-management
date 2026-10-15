package com.sparta.logistics.delivery.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** 배송 구간(SA 5.6 p_delivery_routes). 배송 1건당 2~3건이며 Delivery를 통해서만 바뀐다. */
@Getter
@Entity
@Table(name = "p_delivery_routes")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryRoute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "delivery_route_id")
    private UUID id;

    /** 진행 순서(1부터) */
    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", nullable = false, length = 20)
    private RouteType routeType;

    /** HUB_TRANSFER만 p_hub_routes 값으로 채운다 */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "distanceKm", column = @Column(name = "expected_distance_km", precision = 8, scale = 2)),
            @AttributeOverride(name = "durationMinutes", column = @Column(name = "expected_duration_minutes"))
    })
    private RouteInfo expected;

    /** 도착 처리 때 입력한다 */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "distanceKm", column = @Column(name = "actual_distance_km", precision = 8, scale = 2)),
            @AttributeOverride(name = "durationMinutes", column = @Column(name = "actual_duration_minutes"))
    })
    private RouteInfo actual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    /** 출발 허브. PICKUP은 업체에서 출발하므로 null */
    @Column(name = "departure_hub_id")
    private UUID departureHubId;

    /** 도착 허브. LAST_MILE은 업체로 도착하므로 null */
    @Column(name = "arrival_hub_id")
    private UUID arrivalHubId;

    /** 구간 담당 배송담당자. user-service p_users.user_id 논리 참조 */
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
}
