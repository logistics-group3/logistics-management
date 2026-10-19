package com.sparta.logistics.hub.infrastructure.persistence;

import static com.sparta.logistics.hub.domain.QHub.hub;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.hub.domain.Hub;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class HubQueryRepositoryImpl implements HubQueryRepository {

    /** H-01 정렬 허용 필드(SA 6.5). 여기에 없는 필드는 400 VALIDATION_ERROR가 된다. */
    private static final Map<String, ComparableExpressionBase<?>> SORT_FIELDS = Map.of(
            "createdAt", hub.createdAt,
            "name", hub.name);

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Hub> search(String keyword, Pageable pageable) {
        List<Hub> content = queryFactory.selectFrom(hub)
                .where(keywordContains(keyword))
                .orderBy(orderBy(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory.select(hub.count())
                .from(hub)
                .where(keywordContains(keyword));

        // Note: 마지막 페이지이거나 결과가 한 페이지보다 적으면 count 쿼리를 생략한다. 목록과 count를 분리해 count에는 정렬을 붙이지 않는다.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /** 이름 또는 주소 부분 일치(대소문자 무시). keyword가 비면 조건을 달지 않는다(where에서 null은 무시된다). */
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return hub.name.containsIgnoreCase(trimmed).or(hub.address.containsIgnoreCase(trimmed));
    }

    /**
     * Note: 사용자 정렬은 허용 목록으로만 경로를 바꾼다(QuerydslSupport). createdAt이 같은 행(시드를 한 트랜잭션에 넣으면 흔하다)이
     * 페이지마다 다른 순서로 나오지 않도록 PK를 마지막 정렬 기준으로 덧붙인다.
     */
    private OrderSpecifier<?>[] orderBy(Pageable pageable) {
        OrderSpecifier<?>[] requested = QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORT_FIELDS);
        OrderSpecifier<?>[] withTieBreaker = Arrays.copyOf(requested, requested.length + 1);
        withTieBreaker[requested.length] = hub.id.asc();
        return withTieBreaker;
    }
}
