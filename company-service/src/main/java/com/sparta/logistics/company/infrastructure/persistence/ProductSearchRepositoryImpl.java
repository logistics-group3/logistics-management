package com.sparta.logistics.company.infrastructure.persistence;

import static com.sparta.logistics.company.domain.QCompany.company;
import static com.sparta.logistics.company.domain.QProduct.product;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.logistics.common.querydsl.QuerydslSupport;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    /** P-02 정렬 허용 필드(SA 6.6) */
    private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
            "createdAt", product.createdAt,
            "name", product.name,
            "price", product.price);

    private final JPAQueryFactory queryFactory;

    /**
     * Note: 응답에 업체의 hubId가 들어가므로(SA 6.6 ProductResponse) 업체를 fetch join해 상품마다 업체를 다시 조회하는 N+1을 막는다.
     * ManyToOne fetch join은 행 수가 늘지 않아 페이징과 함께 써도 된다.
     */
    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        List<Product> content = queryFactory.selectFrom(product)
                .join(product.company, company).fetchJoin()
                .where(nameContains(condition.name()), companyIdEq(condition.companyId()), hubIdEq(condition.hubId()))
                .orderBy(QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORTS))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(product.count())
                .from(product)
                .join(product.company, company)
                .where(nameContains(condition.name()), companyIdEq(condition.companyId()), hubIdEq(condition.hubId()));
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? product.name.containsIgnoreCase(name) : null;
    }

    private BooleanExpression companyIdEq(UUID companyId) {
        return companyId != null ? company.id.eq(companyId) : null;
    }

    private BooleanExpression hubIdEq(UUID hubId) {
        return hubId != null ? company.hubId.eq(hubId) : null;
    }
}
