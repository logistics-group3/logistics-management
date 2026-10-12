package com.sparta.logistics.common.web;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Pageable의 Sort → QueryDSL OrderSpecifier. API별 "정렬 허용 필드"만 사용한다 (임의 컬럼 정렬 방지).
 * <pre>
 * private static final Set&lt;String&gt; SORTABLE = Set.of("createdAt", "name");
 * query.orderBy(QuerydslSorts.toOrderSpecifiers(pageable.getSort(), hub, SORTABLE))
 * </pre>
 */
public final class QuerydslSorts {

    private QuerydslSorts() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static OrderSpecifier<?>[] toOrderSpecifiers(Sort sort, EntityPath<?> entity, Set<String> allowed) {
        PathBuilder<?> root = new PathBuilder<>(entity.getType(), entity.getMetadata());
        List<OrderSpecifier<?>> specifiers = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                continue;
            }
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            specifiers.add(new OrderSpecifier(direction, root.getComparable(order.getProperty(), Comparable.class)));
        }
        if (specifiers.isEmpty()) {
            specifiers.add(new OrderSpecifier(Order.DESC, root.getComparable("createdAt", Comparable.class)));
        }
        return specifiers.toArray(new OrderSpecifier[0]);
    }
}
