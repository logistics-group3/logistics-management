package com.sparta.logistics.order.infrastructure.persistence;

import static com.sparta.logistics.order.domain.QOrder.order;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderQueryRepository;
import com.sparta.logistics.order.domain.OrderSearchCriteria;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepositoryImpl implements OrderQueryRepository {

    private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
            "createdAt", order.createdAt, "quantity", order.quantity);

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Order> search(OrderSearchCriteria criteria, Pageable pageable) {
        BooleanExpression[] filters = {
                criteria.ordererId() == null ? null : order.ordererId.eq(criteria.ordererId()),
                criteria.status() == null ? null : order.status.eq(criteria.status()),
                criteria.supplierCompanyId() == null ? null : order.supplierCompanyId.eq(criteria.supplierCompanyId()),
                criteria.receiverCompanyId() == null ? null : order.receiverCompanyId.eq(criteria.receiverCompanyId()),
                criteria.productId() == null ? null : order.productId.eq(criteria.productId()),
                criteria.createdFrom() == null ? null : order.createdAt.goe(criteria.createdFrom()),
                criteria.createdBefore() == null ? null : order.createdAt.lt(criteria.createdBefore())
        };
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        List<Order> content = queryFactory.selectFrom(order)
                .where(filters)
                .orderBy(QuerydslSupport.toOrderSpecifiers(sort, SORTS))
                // Note: 정렬 값이 같아도 ID로 순서를 고정해 페이지 사이의 동률 순서가 바뀌지 않게 한다.
                .orderBy(order.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        // Note: 내용과 count에 같은 조건을 쓴다. @SQLRestriction은 두 JPQL 쿼리 모두에서 삭제 주문을 제외한다.
        JPAQuery<Long> count = queryFactory.select(order.count()).from(order).where(filters);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }
}
