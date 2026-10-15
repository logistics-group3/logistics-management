package com.sparta.logistics.company.infrastructure.persistence;

import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanySearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** QueryDSL로 구현하는 업체 검색 조각. 구현은 CompanySearchRepositoryImpl이다. */
public interface CompanySearchRepository {

    Page<Company> search(CompanySearchCondition condition, Pageable pageable);
}
