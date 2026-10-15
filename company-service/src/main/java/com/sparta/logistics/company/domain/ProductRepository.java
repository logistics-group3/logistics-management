package com.sparta.logistics.company.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * domain이 필요로 하는 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다.
 * 검색(P-02)은 infrastructure 단계에서 QueryDSL 구현과 함께 추가한다.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    /** C-05에서 업체와 함께 논리 삭제할 소속 상품을 찾는다. */
    List<Product> findAllByCompanyId(UUID companyId);
}
