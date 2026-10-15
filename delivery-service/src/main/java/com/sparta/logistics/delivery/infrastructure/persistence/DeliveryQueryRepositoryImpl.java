package com.sparta.logistics.delivery.infrastructure.persistence;

import static com.sparta.logistics.delivery.domain.QDelivery.delivery;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliverySearchCondition;
import com.sparta.logistics.delivery.domain.DeliveryStatus;
import com.sparta.logistics.delivery.domain.QDeliveryRoute;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class DeliveryQueryRepositoryImpl implements DeliveryQueryRepository {

    /** D-01 정렬 허용 필드(SA 6.8) */
    private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
            "createdAt", delivery.createdAt,
            "status", delivery.status);

    private final JPAQueryFactory queryFactory;

    /**
     * D-01 배송 검색. 목록 응답에는 구간이 없으므로 배송만 조회한다(구간 지연 로딩이 일어나지 않아 N+1이 없다).
     * Note: "담당 구간이 있는 배송"은 구간 조인 대신 EXISTS 서브쿼리로 거른다. 조인하면 한 배송에 담당 구간이 둘이면
     * 행이 중복되어 페이지 크기와 count가 틀어진다.
     */
    @Override
    public Page<Delivery> search(DeliverySearchCondition condition, Pageable pageable) {
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
        List<Delivery> content = queryFactory.selectFrom(delivery)
                .where(where)
                .orderBy(QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORTS))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(delivery.count()).from(delivery).where(where);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
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
