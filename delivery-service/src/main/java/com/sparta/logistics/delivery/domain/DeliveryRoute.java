package com.sparta.logistics.delivery.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 배송 구간(SA 5.6 p_delivery_routes). 배송 1건당 2~3건이며 Delivery를 통해서만 바뀐다.
 * 상태 변경 메서드는 package-private이라 같은 패키지의 Delivery만 부를 수 있다.
 */
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

    /** 구간 담당 허브. 배정·재배정 대상은 이 허브 소속 배송담당자다. 배정 작업이 허브별 대기 구간을 찾을 때 쓴다. */
    @Column(name = "managing_hub_id", nullable = false)
    private UUID managingHubId;

    /** 구간 담당 배송담당자. user-service p_users.user_id 논리 참조. NULL이면 배정 대기(결정 로그 27) */
    @Column(name = "manager_id")
    private UUID managerId;

    /** 담당자가 처음 정해진 시각. 배정 지연을 잰다. */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 담당자 없이 만든다. 담당자는 배송 생성 뒤 허브별 배정 작업이 채운다. */
    DeliveryRoute(Delivery delivery, int sequence, RouteType routeType, UUID departureHubId, UUID arrivalHubId,
                  RouteInfo expected) {
        this.delivery = delivery;
        this.sequence = sequence;
        this.routeType = routeType;
        this.departureHubId = departureHubId;
        this.arrivalHubId = arrivalHubId;
        this.managingHubId = routeType.managingHubId(departureHubId, arrivalHubId);
        this.expected = expected;
        this.status = RouteStatus.WAITING;
    }

    /** 배정 대기: 담당자가 없고 아직 출발 전(WAITING)이다. 취소된 구간은 배정하지 않는다. */
    public boolean isAwaitingManager() {
        return managerId == null && status == RouteStatus.WAITING;
    }

    /** 자동 배정. 배정 대기 구간에만 담당자를 넣는다. 이미 담당자가 있으면 그대로 둔다(배정 작업이 여러 번 불려도 결과가 같다). */
    boolean assignManager(UUID managerId) {
        if (!isAwaitingManager()) {
            return false;
        }
        this.managerId = managerId;
        this.assignedAt = LocalDateTime.now();
        return true;
    }


    /** WAITING → MOVING. 담당자가 정해지지 않은 구간은 출발할 수 없다(결정 로그 27). */
    void start() {
        requireStatus(RouteStatus.WAITING);
        if (managerId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = RouteStatus.MOVING;
    }

    /** MOVING → ARRIVED. 허브 이동은 실제 거리·시간이 둘 다 있어야 한다(SA 6.8 D-05). */
    void arrive(RouteInfo actual) {
        requireStatus(RouteStatus.MOVING);
        if (routeType == RouteType.HUB_TRANSFER && (actual == null || !actual.hasBoth())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "허브 이동 구간 도착에는 실제 거리와 소요시간이 필요합니다.");
        }
        this.actual = actual == null || actual.isEmpty() ? null : actual;
        this.status = RouteStatus.ARRIVED;
    }

    /** WAITING → CANCELED. 배송 취소로만 일어난다. */
    void cancel() {
        requireStatus(RouteStatus.WAITING);
        this.status = RouteStatus.CANCELED;
    }

    /** CANCELED → WAITING. 취소 원복(I-12)으로만 일어난다. */
    void revertCancel() {
        requireStatus(RouteStatus.CANCELED);
        this.status = RouteStatus.WAITING;
    }

    /** 도착·취소된 구간은 담당자를 바꿀 수 없다(SA 6.8 D-04). */
    void changeManager(UUID managerId) {
        if (status == RouteStatus.ARRIVED || status == RouteStatus.CANCELED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (this.assignedAt == null) {
            this.assignedAt = LocalDateTime.now();
        }
        this.managerId = managerId;
    }

    private void requireStatus(RouteStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
