package com.sparta.logistics.hub.infrastructure.persistence;

import static com.sparta.logistics.hub.domain.QHubStock.hubStock;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.hub.domain.HubStock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class HubStockQueryRepositoryImpl implements HubStockQueryRepository {

    /** H-05 정렬 허용 필드(SA 6.5). 여기에 없는 필드는 400 VALIDATION_ERROR가 된다. */
    private static final Map<String, ComparableExpressionBase<?>> SORT_FIELDS = Map.of(
            "createdAt", hubStock.createdAt,
            "quantity", hubStock.quantity);

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<HubStock> search(UUID hubId, UUID productId, Pageable pageable) {
        List<HubStock> content = queryFactory.selectFrom(hubStock)
                .where(hubStock.hubId.eq(hubId), productEq(productId))
                .orderBy(orderBy(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory.select(hubStock.count())
                .from(hubStock)
                .where(hubStock.hubId.eq(hubId), productEq(productId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression productEq(UUID productId) {
        return productId == null ? null : hubStock.productId.eq(productId);
    }

    /** createdAt·quantity가 같은 행이 페이지마다 다른 순서로 나오지 않도록 PK를 마지막 정렬 기준으로 덧붙인다. */
    private OrderSpecifier<?>[] orderBy(Pageable pageable) {
        OrderSpecifier<?>[] requested = QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORT_FIELDS);
        OrderSpecifier<?>[] withTieBreaker = Arrays.copyOf(requested, requested.length + 1);
        withTieBreaker[requested.length] = hubStock.id.asc();
        return withTieBreaker;
    }
}
