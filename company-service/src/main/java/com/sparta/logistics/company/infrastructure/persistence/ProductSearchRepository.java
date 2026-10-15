package com.sparta.logistics.company.infrastructure.persistence;

import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** QueryDSL로 구현하는 상품 검색 조각. 구현은 ProductSearchRepositoryImpl이다. */
public interface ProductSearchRepository {

    Page<Product> search(ProductSearchCondition condition, Pageable pageable);
}
