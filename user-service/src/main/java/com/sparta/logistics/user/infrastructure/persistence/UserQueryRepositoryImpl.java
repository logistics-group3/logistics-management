package com.sparta.logistics.user.infrastructure.persistence;

import static com.sparta.logistics.user.domain.QUser.user;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class UserQueryRepositoryImpl implements UserQueryRepository {

    /** SA U-05 정렬 허용 필드. password처럼 정렬 순서로 값을 추측할 수 있는 필드를 막는다. */
    private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
            "createdAt", user.createdAt,
            "username", user.username);

    private final JPAQueryFactory queryFactory;

    /** 탈퇴자는 User의 @SQLRestriction이 QueryDSL(JPQL) 조회에도 붙어 빠진다. */
    @Override
    public Page<User> search(UserSearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {
                usernameContains(condition.username()),
                nameContains(condition.name()),
                roleEq(condition.role()),
                deliveryManagerEq(condition.isDeliveryManager()),
                hubIdEq(condition.hubId())
        };
        List<User> content = queryFactory.selectFrom(user)
                .where(where)
                .orderBy(QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORTS))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        // Note: 마지막 페이지이거나 첫 페이지에서 결과가 size보다 적으면 전체 개수를 이미 알 수 있어 count 쿼리를 건너뛴다.
        JPAQuery<Long> count = queryFactory.select(user.count()).from(user).where(where);
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    private BooleanExpression usernameContains(String username) {
        return StringUtils.hasText(username) ? user.username.contains(username) : null;
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? user.name.containsIgnoreCase(name) : null;
    }

    private BooleanExpression roleEq(Role role) {
        return role != null ? user.role.eq(role) : null;
    }

    private BooleanExpression deliveryManagerEq(Boolean deliveryManager) {
        return deliveryManager != null ? user.deliveryManager.eq(deliveryManager) : null;
    }

    private BooleanExpression hubIdEq(UUID hubId) {
        return hubId != null ? user.hubId.eq(hubId) : null;
    }
}
