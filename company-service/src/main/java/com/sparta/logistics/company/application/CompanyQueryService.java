package com.sparta.logistics.company.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.application.dto.CompanyResult;
import com.sparta.logistics.company.application.dto.InternalCompanyResult;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.CompanySearchCondition;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyQueryService {

    private final CompanyRepository companyRepository;

    /** C-02 업체 검색. 정렬은 createdAt, name만 허용한다. */
    public Page<CompanyResult> searchCompanies(CompanySearchCondition condition, Pageable pageable) {
        return companyRepository.search(condition, pageable).map(CompanyResult::from);
    }

    /** C-03 업체 단건 조회. 없거나 삭제됐으면 COMPANY_NOT_FOUND */
    public CompanyResult getCompany(UUID companyId) {
        return CompanyResult.from(loadCompany(companyId));
    }

    /** I-07 업체 조회(order 호출). 삭제된 업체는 @SQLRestriction으로 조회되지 않아 404다. */
    public InternalCompanyResult getInternalCompany(UUID companyId) {
        return InternalCompanyResult.from(loadCompany(companyId));
    }

    private Company loadCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
    }
}
