package com.sparta.logistics.company.infrastructure.persistence;

import static com.sparta.logistics.company.domain.QCompany.company;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanySearchCondition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class CompanySearchRepositoryImpl implements CompanySearchRepository {

    /** C-02 정렬 허용 필드(SA 6.6) */
    private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
            "createdAt", company.createdAt,
            "name", company.name);

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Company> search(CompanySearchCondition condition, Pageable pageable) {
        List<Company> content = queryFactory.selectFrom(company)
                .where(nameContains(condition.name()), hubIdEq(condition.hubId()), managerIdEq(condition.managerId()))
                .orderBy(QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORTS))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(company.count())
                .from(company)
                .where(nameContains(condition.name()), hubIdEq(condition.hubId()), managerIdEq(condition.managerId()));
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? company.name.containsIgnoreCase(name) : null;
    }

    private BooleanExpression hubIdEq(UUID hubId) {
        return hubId != null ? company.hubId.eq(hubId) : null;
    }

    private BooleanExpression managerIdEq(UUID managerId) {
        return managerId != null ? company.managerId.eq(managerId) : null;
    }
}
