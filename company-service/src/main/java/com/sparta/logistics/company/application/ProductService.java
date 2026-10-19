package com.sparta.logistics.company.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.application.dto.CreateProductCommand;
import com.sparta.logistics.company.application.dto.ProductResult;
import com.sparta.logistics.company.application.dto.UpdateProductCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품은 업체의 하위 리소스라 소유자 검증을 항상 소속 업체의 관리자로 한다. */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;

    /** P-01 상품 생성. 업체 관리자 본인 또는 MASTER. 다른 서비스는 호출하지 않는다. */
    @Transactional
    public ProductResult create(UserContext user, UUID companyId, CreateProductCommand command) {
        Company company = companyRepository.findByIdForUpdate(companyId)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
        user.validateSelfOrMaster(company.getManagerId());
        Product product = Product.create(company, command.name(), command.description(), command.price());
        return ProductResult.from(productRepository.save(product));
    }

    /** P-04 상품 수정. 기존 주문은 가격 스냅샷을 갖고 있어 영향이 없다. */
    @Transactional
    public ProductResult update(UserContext user, UUID companyId, UUID productId, UpdateProductCommand command) {
        Product product = getProductOfCompany(companyId, productId);
        user.validateSelfOrMaster(product.getCompany().getManagerId());
        product.update(command.name(), command.description(), command.price());
        return ProductResult.from(product);
    }

    /** P-05 상품 삭제. 삭제 후 I-08은 404를 돌려줘 주문할 수 없다. */
    @Transactional
    public void delete(UserContext user, UUID companyId, UUID productId) {
        Product product = getProductOfCompany(companyId, productId);
        user.validateSelfOrMaster(product.getCompany().getManagerId());
        product.softDelete(user.username());
    }

    /** 경로의 업체 소속이 아닌 상품도 PRODUCT_NOT_FOUND로 응답한다(SA P-03). */
    private Product getProductOfCompany(UUID companyId, UUID productId) {
        return productRepository.findById(productId)
                .filter(product -> product.belongsTo(companyId))
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.PRODUCT_NOT_FOUND));
    }
}
