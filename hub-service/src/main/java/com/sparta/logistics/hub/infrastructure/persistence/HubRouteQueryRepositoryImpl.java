package com.sparta.logistics.hub.infrastructure.persistence;

import static com.sparta.logistics.hub.domain.QHubRoute.hubRoute;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.hub.domain.HubRoute;
import com.sparta.logistics.hub.domain.QHub;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class HubRouteQueryRepositoryImpl implements HubRouteQueryRepository {

    /** H-03 정렬 허용 필드(SA 6.5). 여기에 없는 필드는 400 VALIDATION_ERROR가 된다. */
    private static final Map<String, ComparableExpressionBase<?>> SORT_FIELDS = Map.of(
            "createdAt", hubRoute.createdAt,
            "distanceKm", hubRoute.distanceKm,
            "durationMinutes", hubRoute.durationMinutes);

    // Note: 같은 Hub 테이블을 출발·도착으로 두 번 조인하므로 별칭이 다른 Q타입이 둘 필요하다. QHub.hub를 두 번 쓰면 별칭이 충돌한다.
    private static final QHub DEPARTURE_HUB = new QHub("departureHub");
    private static final QHub ARRIVAL_HUB = new QHub("arrivalHub");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<HubRoute> search(UUID departureHubId, UUID arrivalHubId, Pageable pageable) {
        // Note: to-one fetch join이라 페이징 결과 행 수가 늘지 않아 DB에서 그대로 offset·limit을 적용할 수 있다. 허브명 조회 N+1을 막는다.
        List<HubRoute> content = queryFactory.selectFrom(hubRoute)
                .join(hubRoute.departureHub, DEPARTURE_HUB).fetchJoin()
                .join(hubRoute.arrivalHub, ARRIVAL_HUB).fetchJoin()
                .where(departureHubIdEq(departureHubId), arrivalHubIdEq(arrivalHubId))
                .orderBy(orderBy(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // Note: count에는 fetch join과 정렬을 붙이지 않는다. 필터가 FK 컬럼(허브 ID)만 쓰므로 hubRoute 단독으로 센다.
        JPAQuery<Long> countQuery =queryFactory.select(hubRoute.count())
                .from(hubRoute)
                .where(departureHubIdEq(departureHubId), arrivalHubIdEq(arrivalHubId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression departureHubIdEq(UUID departureHubId) {
        return departureHubId == null ? null : hubRoute.departureHub.id.eq(departureHubId);
    }

    private BooleanExpression arrivalHubIdEq(UUID arrivalHubId) {
        return arrivalHubId == null ? null : hubRoute.arrivalHub.id.eq(arrivalHubId);
    }

    private OrderSpecifier<?>[] orderBy(Pageable pageable) {
        OrderSpecifier<?>[] requested = QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORT_FIELDS);
        OrderSpecifier<?>[] withTieBreaker = Arrays.copyOf(requested, requested.length + 1);
        withTieBreaker[requested.length] = hubRoute.id.asc();
        return withTieBreaker;
    }
}
