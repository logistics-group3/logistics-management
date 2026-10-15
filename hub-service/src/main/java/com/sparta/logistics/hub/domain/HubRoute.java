package com.sparta.logistics.hub.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** 방향이 있는 허브 간 경로(A→B와 B→A는 별개 행). 17 × 16 = 272건을 적재하고 조회만 한다(SA 5.3). */
@Getter
@Entity
@Table(name = "p_hub_routes")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubRoute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hub_route_id")
    private UUID id;

    // Note: 같은 서비스 테이블이라 서비스 간 UUID 참조가 아닌 실제 FK·연관관계를 쓴다. LAZY라서 목록 조회에서 허브명을 읽으면 경로마다 허브 조회가 나가므로(N+1) 조회 쿼리에서 fetch join으로 함께 가져온다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_hub_id", nullable = false, updatable = false)
    private Hub departureHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arrival_hub_id", nullable = false, updatable = false)
    private Hub arrivalHub;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    private HubRoute(Hub departureHub, Hub arrivalHub, BigDecimal distanceKm, int durationMinutes) {
        this.departureHub = departureHub;
        this.arrivalHub = arrivalHub;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
    }

    /** DB CHECK와 같은 규칙을 생성 시점에 먼저 검증한다. 위반은 비즈니스 규칙 위반(INVALID_REQUEST)으로 던진다. */
    public static HubRoute create(Hub departureHub, Hub arrivalHub, BigDecimal distanceKm, int durationMinutes) {
        if (departureHub == null || arrivalHub == null) {
            throw invalid("출발·도착 허브는 필수입니다.");
        }
        if (departureHub == arrivalHub || (departureHub.getId() != null && departureHub.getId().equals(arrivalHub.getId()))) {
            throw invalid("출발 허브와 도착 허브는 달라야 합니다.");
        }
        if (distanceKm == null || distanceKm.signum() <= 0) {
            throw invalid("이동거리는 0보다 커야 합니다.");
        }
        if (durationMinutes <= 0) {
            throw invalid("소요시간은 0보다 커야 합니다.");
        }
        return new HubRoute(departureHub, arrivalHub, distanceKm, durationMinutes);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST, message);
    }
}
