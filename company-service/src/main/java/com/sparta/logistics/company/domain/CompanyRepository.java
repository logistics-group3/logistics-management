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

    /** C-02. 정렬은 createdAt, name만 허용한다. */
    Page<Company> search(CompanySearchCondition condition, Pageable pageable);
}
