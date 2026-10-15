package com.sparta.logistics.company.application;

import static com.sparta.logistics.company.CompanyFixture.company;
import static com.sparta.logistics.company.CompanyFixture.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.application.dto.InternalCompanyResult;
import com.sparta.logistics.company.application.dto.InternalProductResult;
import com.sparta.logistics.company.application.dto.ProductResult;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CompanyQueryService, ProductQueryService의 단건·내부 조회. 검색은 CompanySearchIntegrationTest가 확인한다. */
@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProductRepository productRepository;
    private CompanyQueryService companyQueryService;
    private ProductQueryService productQueryService;

    @BeforeEach
    void setUp() {
        companyQueryService = new CompanyQueryService(companyRepository);
        productQueryService = new ProductQueryService(productRepository);
    }

    @Test
    @DisplayName("I-07은 업체의 허브, 주소, 관리자를 돌려준다")
    void getInternalCompany() {
        Company company = company(UUID.randomUUID());
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));

        InternalCompanyResult result = companyQueryService.getInternalCompany(company.getId());

        assertThat(result.hubId()).isEqualTo(company.getHubId());
        assertThat(result.managerId()).isEqualTo(company.getManagerId());
        assertThat(result.address()).isEqualTo(company.getAddress());
    }

    @Test
    @DisplayName("없거나 삭제된 업체를 조회하면 COMPANY_NOT_FOUND")
    void getUnknownCompany() {
        UUID companyId = UUID.randomUUID();
        given(companyRepository.findById(companyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyQueryService.getCompany(companyId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("P-03은 경로의 업체 소속 상품이면 업체의 hubId를 담아 돌려준다")
    void getProduct() {
        Company company = company(UUID.randomUUID());
        Product product = product(company);
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        ProductResult result = productQueryService.getProduct(company.getId(), product.getId());

        assertThat(result.hubId()).isEqualTo(company.getHubId());
    }

    @Test
    @DisplayName("P-03에서 다른 업체 소속 상품을 조회하면 PRODUCT_NOT_FOUND")
    void getProductOfOtherCompany() {
        Product product = product(company(UUID.randomUUID()));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        assertThatThrownBy(() -> productQueryService.getProduct(UUID.randomUUID(), product.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CompanyErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("I-08은 상품의 소속 업체와 가격을 돌려준다")
    void getInternalProduct() {
        Company company = company(UUID.randomUUID());
        Product product = product(company);
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        InternalProductResult result = productQueryService.getInternalProduct(product.getId());

        assertThat(result.companyId()).isEqualTo(company.getId());
        assertThat(result.price()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("없거나 삭제된 상품을 I-08로 조회하면 PRODUCT_NOT_FOUND")
    void getUnknownInternalProduct() {
        UUID productId = UUID.randomUUID();
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productQueryService.getInternalProduct(productId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CompanyErrorCode.PRODUCT_NOT_FOUND);
    }
}
