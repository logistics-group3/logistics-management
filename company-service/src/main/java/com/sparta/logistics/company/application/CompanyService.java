package com.sparta.logistics.company.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.company.application.dto.CompanyResult;
import com.sparta.logistics.company.application.dto.CreateCompanyCommand;
import com.sparta.logistics.company.application.dto.UpdateCompanyCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.ProductRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final HubReader hubReader;
    private final UserReader userReader;
    private final TransactionTemplate transactionTemplate;

    /**
     * C-01 업체 생성. USER는 본인만 관리자로, MASTER는 관리자를 지정한다. 허브(I-02) → 지정 관리자(I-01) 순으로 확인한다.
     * Note: 원격 호출 동안 DB 커넥션을 잡지 않도록 메서드에 트랜잭션을 걸지 않는다. 저장은 save 한 번이라 Spring Data의 트랜잭션으로 충분하다.
     */
    public CompanyResult create(UserContext user, CreateCompanyCommand command) {
        UUID managerId = resolveManager(user, command.managerId());
        hubReader.checkExists(command.hubId());
        if (user.isMaster()) {
            userReader.checkExists(managerId);
        }
        Company company = Company.create(command.name(), command.address(), command.hubId(), managerId);
        return CompanyResult.from(companyRepository.save(company));
    }

    /**
     * C-04 업체 수정. 업체 관리자 본인 또는 MASTER, 관리자 변경은 MASTER만 한다.
     * Note: 허브·사용자 확인(원격 호출)은 트랜잭션 밖에서 먼저 하고, 변경만 짧은 트랜잭션으로 한다.
     * 그 사이 업체가 삭제되거나 관리자가 바뀔 수 있어 트랜잭션 안에서 다시 조회하고 검사한다.
     */
    @CacheEvict(cacheNames = CompanyCacheNames.INTERNAL_COMPANY, key = "#companyId")
    public CompanyResult update(UserContext user, UUID companyId, UpdateCompanyCommand command) {
        user.validateSelfOrMaster(getCompany(companyId).getManagerId());
        if (command.managerId() != null) {
            user.validateMaster();
            userReader.checkExists(command.managerId());
        }
        if (command.hubId() != null) {
            hubReader.checkExists(command.hubId());
        }
        return transactionTemplate.execute(status -> {
            // Note: 잠근 최신 행으로 검사한다. 일반 조회면 그 사이 완료된 관리자 변경을 전체 컬럼 UPDATE가 되돌린다.
            Company company = companyRepository.findByIdForUpdate(companyId)
                    .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
            user.validateSelfOrMaster(company.getManagerId());
            company.update(command.name(), command.address());
            if (command.hubId() != null) {
                company.changeHub(command.hubId());
            }
            if (command.managerId() != null) {
                company.assignManager(command.managerId());
            }
            return CompanyResult.from(company);
        });
    }

    /** C-05 업체 삭제. 업체와 소속 상품을 한 로컬 트랜잭션에서 논리 삭제한다(SA 7.5). */
    // Note: 소속 상품도 함께 삭제되는데 상품 키는 상품 ID라 업체 ID로 고를 수 없다. 업체 삭제는 드물어 상품 캐시를 모두 비운다.
    @Caching(evict = {
            @CacheEvict(cacheNames = CompanyCacheNames.INTERNAL_COMPANY, key = "#companyId"),
            @CacheEvict(cacheNames = CompanyCacheNames.INTERNAL_PRODUCT, allEntries = true)
    })
    @Transactional
    public void delete(UserContext user, UUID companyId) {
        Company company = companyRepository.findByIdForUpdate(companyId)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
        user.validateSelfOrMaster(company.getManagerId());
        productRepository.findAllByCompanyId(companyId)
                .forEach(product -> product.softDelete(user.username()));
        company.softDelete(user.username());
    }

    /** USER가 다른 사람을 관리자로 지정하면 403, MASTER가 관리자를 비우면 400(SA C-01). */
    private UUID resolveManager(UserContext user, UUID requestedManagerId) {
        if (user.isMaster()) {
            if (requestedManagerId == null) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "MASTER는 업체 관리자(managerId)를 지정해야 합니다.");
            }
            return requestedManagerId;
        }
        if (requestedManagerId != null && !user.isSelf(requestedManagerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return user.userId();
    }

    private Company getCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
    }
}
