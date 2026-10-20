package com.sparta.logistics.company.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * domain이 필요로 하는 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다.
 */
public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);

    /** 업체 삭제와 상품 생성이 같은 업체의 활성 상태를 잠금 아래 확인한다. */
    Optional<Company> findByIdForUpdate(UUID id);

    /** C-02. 정렬은 createdAt, name만 허용한다. */
    Page<Company> search(CompanySearchCondition condition, Pageable pageable);
}
