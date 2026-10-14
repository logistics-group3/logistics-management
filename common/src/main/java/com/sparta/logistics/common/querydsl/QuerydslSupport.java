package com.sparta.logistics.common.querydsl;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.DateTimePath;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * QueryDSL 보조. Q클래스와 쿼리는 각 서비스에 둔다 (ADR-15).
 * 사용자가 보낸 정렬 필드를 그대로 orderBy에 넣지 않고, 서비스가 정한 허용 목록(Map)으로만 변환한다 (CVE-2024-49203).
 */
public final class QuerydslSupport {

    private QuerydslSupport() {
    }

    /** 논리 삭제 제외 조건. 예: {@code notDeleted(qHub.deletedAt)} */
    public static BooleanExpression notDeleted(DateTimePath<LocalDateTime> deletedAt) {
        return deletedAt.isNull();
    }

    /**
     * @param allowed 정렬 필드명 → Q클래스 경로. 예: {@code Map.of("createdAt", qHub.createdAt, "name", qHub.name)}
     */
    public static OrderSpecifier<?>[] toOrderSpecifiers(Sort sort,
                                                        Map<String, ? extends ComparableExpressionBase<?>> allowed) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            ComparableExpressionBase<?> path = allowed.get(order.getProperty());
            if (path == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "허용되지 않은 정렬 필드입니다: " + order.getProperty());
            }
            orders.add(new OrderSpecifier<>(order.isAscending() ? Order.ASC : Order.DESC, path));
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
