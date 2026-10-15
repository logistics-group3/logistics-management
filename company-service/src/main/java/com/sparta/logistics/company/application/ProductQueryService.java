package com.sparta.logistics.company.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.application.dto.InternalProductResult;
import com.sparta.logistics.company.application.dto.ProductResult;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import com.sparta.logistics.company.domain.ProductSearchCondition;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;

    /** P-02 상품 검색. hubId는 소속 업체의 관리 허브로 거른다. 정렬은 createdAt, name, price만 허용한다. */
    public Page<ProductResult> searchProducts(ProductSearchCondition condition, Pageable pageable) {
        return productRepository.search(condition, pageable).map(ProductResult::from);
    }

    /** P-03 상품 단건 조회. 경로의 업체 소속이 아니면 PRODUCT_NOT_FOUND */
    public ProductResult getProduct(UUID companyId, UUID productId) {
        Product product = loadProduct(productId);
        if (!product.belongsTo(companyId)) {
            throw new BusinessException(CompanyErrorCode.PRODUCT_NOT_FOUND);
        }
        return ProductResult.from(product);
    }

    /** I-08 상품 조회(order 호출). 삭제된 상품은 조회되지 않아 404다. */
    public InternalProductResult getInternalProduct(UUID productId) {
        return InternalProductResult.from(loadProduct(productId));
    }

    private Product loadProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.PRODUCT_NOT_FOUND));
    }
}
