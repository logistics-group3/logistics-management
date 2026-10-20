package com.sparta.logistics.delivery.infrastructure.persistence;

import static com.sparta.logistics.delivery.domain.QDelivery.delivery;
import static com.sparta.logistics.delivery.domain.QDeliveryRoute.deliveryRoute;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.application.dto.DeliveryRouteResult;
import com.sparta.logistics.delivery.application.dto.DeliverySearchCondition;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.application.dto.QActiveRouteCount;
import com.sparta.logistics.delivery.application.dto.QDeliveryRouteResult;
import com.sparta.logistics.delivery.application.dto.QDeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.QDeliverySummaryResult;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import com.sparta.logistics.delivery.domain.QDeliveryRoute;
import com.sparta.logistics.delivery.domain.RouteStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * 배송 조회 구현(QueryDSL). 모든 메서드가 Entity가 아니라 DTO를 바로 만든다.
 * Note: 프로젝션은 필요한 컬럼만 SELECT하고 영속성 컨텍스트에 Entity를 올리지 않는다. 변경 감지·지연 로딩이 없어 조회가 가볍다.
 * @SQLRestriction("deleted_at IS NULL")은 FROM에 Entity가 오면 프로젝션 쿼리에도 붙으므로 논리 삭제된 행은 나오지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class DeliveryQueryRepositoryImpl implements DeliveryQueryRepository {

    /** D-01 정렬 허용 필드(SA 6.8) */
    private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
            "createdAt", delivery.createdAt,
            "status", delivery.status);

    private final JPAQueryFactory queryFactory;

    /**
     * D-01 배송 검색. 목록 응답에는 구간이 없으므로 배송 컬럼만 읽는다.
     * Note: "담당 구간이 있는 배송"은 구간 조인 대신 EXISTS 서브쿼리로 거른다. 조인하면 한 배송에 담당 구간이 둘이면
     * 행이 중복되어 페이지 크기와 count가 틀어진다.
     */
    @Override
    public Page<DeliverySummaryResult> search(DeliverySearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {
                statusEq(condition.status()),
                eq(delivery.departureHubId, condition.departureHubId()),
                eq(delivery.destinationHubId, condition.destinationHubId()),
                eq(delivery.orderId, condition.orderId()),
                eq(delivery.supplierCompanyId, condition.supplierCompanyId()),
                eq(delivery.receiverCompanyId, condition.receiverCompanyId()),
                hasRouteManagedBy("managerRoute", condition.managerId()),
                hasRouteManagedBy("scopeRoute", condition.scopeManagerId())
        };
        List<DeliverySummaryResult> content = queryFactory.select(summary())
                .from(delivery)
                .where(where)
                .orderBy(QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORTS))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(delivery.count()).from(delivery).where(where);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    @Override
    public Optional<DeliverySummaryResult> findSummary(UUID deliveryId) {
        return Optional.ofNullable(queryFactory.select(summary())
                .from(delivery)
                .where(delivery.id.eq(deliveryId))
                .fetchOne());
    }

    /**
     * D-02 구간 목록. 배송 1건과 구간 N건을 한 번에 프로젝션하면 배송 컬럼이 구간 수만큼 중복되므로
     * 배송(findSummary)과 구간(이 메서드)을 쿼리 두 번으로 나눠 읽는다.
     */
    @Override
    public List<DeliveryRouteResult> findRoutes(UUID deliveryId) {
        return queryFactory.select(new QDeliveryRouteResult(
                        deliveryRoute.id,
                        deliveryRoute.sequence,
                        deliveryRoute.routeType,
                        deliveryRoute.departureHubId,
                        deliveryRoute.arrivalHubId,
                        deliveryRoute.expected.distanceKm,
                        deliveryRoute.expected.durationMinutes,
                        deliveryRoute.actual.distanceKm,
                        deliveryRoute.actual.durationMinutes,
                        deliveryRoute.status,
                        deliveryRoute.managerId))
                .from(deliveryRoute)
                .where(deliveryRoute.delivery.id.eq(deliveryId))
                .orderBy(deliveryRoute.sequence.asc())
                .fetch();
    }

    @Override
    public Optional<DeliveryStatusResult> findStatus(UUID deliveryId) {
        return Optional.ofNullable(queryFactory.select(new QDeliveryStatusResult(delivery.id, delivery.orderId, delivery.status))
                .from(delivery)
                .where(delivery.id.eq(deliveryId))
                .fetchOne());
    }

    @Override
    public Optional<DeliveryStatusResult> findStatusByOrderId(UUID orderId) {
        return Optional.ofNullable(queryFactory.select(new QDeliveryStatusResult(delivery.id, delivery.orderId, delivery.status))
                .from(delivery)
                .where(delivery.orderId.eq(orderId))
                .fetchOne());
    }

    /**
     * Note: 후보 전원의 건수를 GROUP BY 한 번으로 센다. 후보마다 count 쿼리를 보내면 후보 수만큼 왕복한다.
     * manager_id 인덱스(V1)를 탄다. 실행계획 확인 후 부분 인덱스로 바꿀 후보다(결정 로그 13).
     */
    @Override
    public List<ActiveRouteCount> countActiveRoutes(Collection<UUID> managerIds) {
        return queryFactory.select(new QActiveRouteCount(deliveryRoute.managerId, deliveryRoute.count()))
                .from(deliveryRoute)
                .where(deliveryRoute.managerId.in(managerIds),
                        deliveryRoute.status.in(RouteStatus.WAITING, RouteStatus.MOVING))
                .groupBy(deliveryRoute.managerId)
                .fetch();
    }

    @Override
    public List<UUID> findHubsAwaitingManager() {
        return queryFactory.select(deliveryRoute.managingHubId).distinct()
                .from(deliveryRoute)
                .where(deliveryRoute.managerId.isNull(), deliveryRoute.status.eq(RouteStatus.WAITING))
                .fetch();
    }

    @Override
    public boolean existsRouteAwaitingManager(UUID hubId) {
        return queryFactory.selectOne()
                .from(deliveryRoute)
                .where(deliveryRoute.managingHubId.eq(hubId), deliveryRoute.managerId.isNull(),
                        deliveryRoute.status.eq(RouteStatus.WAITING))
                .fetchFirst() != null;
    }

    private QDeliverySummaryResult summary() {
        return new QDeliverySummaryResult(
                delivery.id,
                delivery.orderId,
                delivery.status,
                delivery.supplierCompanyId,
                delivery.receiverCompanyId,
                delivery.departureHubId,
                delivery.destinationHubId,
                delivery.pickupAddress,
                delivery.deliveryAddress,
                delivery.recipientName,
                delivery.recipientSlackId,
                delivery.createdAt,
                delivery.updatedAt);
    }

    private BooleanExpression statusEq(DeliveryStatus status) {
        return status != null ? delivery.status.eq(status) : null;
    }

    private BooleanExpression eq(ComparablePath<UUID> path, UUID value) {
        return value != null ? path.eq(value) : null;
    }

    /** 두 조건(검색 파라미터, 조회 범위)이 함께 쓰일 수 있어 서브쿼리 별칭을 따로 받는다. */
    private BooleanExpression hasRouteManagedBy(String alias, UUID managerId) {
        if (managerId == null) {
            return null;
        }
        QDeliveryRoute route = new QDeliveryRoute(alias);
        return JPAExpressions.selectOne()
                .from(route)
                .where(route.delivery.eq(delivery), route.managerId.eq(managerId))
                .exists();
    }
}
