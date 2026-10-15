package com.sparta.logistics.company.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * domain이 필요로 하는 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    /** C-05에서 업체와 함께 논리 삭제할 소속 상품을 찾는다. */
    List<Product> findAllByCompanyId(UUID companyId);

    /** P-02. 정렬은 createdAt, name, price만 허용한다. */
    Page<Product> search(ProductSearchCondition condition, Pageable pageable);
}
