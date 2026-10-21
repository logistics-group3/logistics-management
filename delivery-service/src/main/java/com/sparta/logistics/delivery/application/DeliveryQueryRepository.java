package com.sparta.logistics.delivery.application;

import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.application.dto.DeliveryRouteResult;
import com.sparta.logistics.delivery.application.dto.DeliverySearchCondition;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 배송 조회 전용 저장소. Entity를 읽지 않고 필요한 컬럼만 DTO로 바로 돌려준다(결정 로그 17).
 * 명령(생성·변경·삭제)은 domain/DeliveryRepository(JPA)가 맡는다. 구현은 infrastructure/persistence(QueryDSL)에 있다.
 */
public interface DeliveryQueryRepository {

    /** D-01 배송 검색 */
    Page<DeliverySummaryResult> search(DeliverySearchCondition condition, Pageable pageable);

    /** D-02 배송 단건의 배송 부분 */
    Optional<DeliverySummaryResult> findSummary(UUID deliveryId);

    /** D-02 배송 단건의 구간 목록(순번 오름차순) */
    List<DeliveryRouteResult> findRoutes(UUID deliveryId);

    /** I-10 order용 배송 상태 */
    Optional<DeliveryStatusResult> findStatus(UUID deliveryId);

    /** orderId로 읽는 배송 상태(I-17). 논리 삭제된 배송은 없는 것으로 본다. */
    Optional<DeliveryStatusResult> findStatusByOrderId(UUID orderId);

    /** 자동 배정 후보별 담당 건수(WAITING·MOVING 구간 수, 논리 삭제 제외). 담당 구간이 없는 후보는 결과에 없다. */
    List<ActiveRouteCount> countActiveRoutes(Collection<UUID> managerIds);

    /**
     * 배정 대기 구간(담당자 없음, WAITING)이 있는 구간 담당 허브. 주기 작업이 다시 깨울 허브를 찾는다(결정 로그 27).
     * 확정됐고 수동 지정으로 넘기지 않은 배송의 구간만 본다(결정 로그 29).
     */
    List<UUID> findHubsAwaitingManager();

    /** 주문 확정(I-19)을 받지 못한 배송의 주문 ID. createdAfter~createdBefore 사이에 만든 것을 오래된 순으로 최대 limit건 */
    List<UUID> findUnconfirmedOrderIds(LocalDateTime createdAfter, LocalDateTime createdBefore, int limit);

    /** 이 허브에 배정 대기 구간이 남아 있나 */
    boolean existsRouteAwaitingManager(UUID hubId);
}
