package com.sparta.logistics.company.application;

import static com.sparta.logistics.company.CompanyFixture.company;
import static com.sparta.logistics.company.CompanyFixture.master;
import static com.sparta.logistics.company.CompanyFixture.product;
import static com.sparta.logistics.company.CompanyFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.ErrorCode;
import com.sparta.logistics.company.application.dto.CreateProductCommand;
import com.sparta.logistics.company.application.dto.ProductResult;
import com.sparta.logistics.company.application.dto.UpdateProductCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private ProductService productService;

    private static void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    @Test
    @DisplayName("업체 관리자가 상품을 만들면 응답의 hubId는 업체의 관리 허브다")
    void createByManager() {
        UUID managerId = UUID.randomUUID();
        Company company = company(managerId);
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProductResult result = productService.create(user(managerId), company.getId(),
                new CreateProductCommand("생수", "500ml", 1000L));

        assertThat(result.companyId()).isEqualTo(company.getId());
        assertThat(result.hubId()).isEqualTo(company.getHubId());
        assertThat(result.price()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("업체 관리자가 아니면 상품을 만들 수 없다")
    void createByOtherUser() {
        Company company = company(UUID.randomUUID());
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));

        assertErrorCode(() -> productService.create(user(UUID.randomUUID()), company.getId(),
                new CreateProductCommand("생수", null, 1000L)), CommonErrorCode.FORBIDDEN);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 업체에 상품을 만들면 COMPANY_NOT_FOUND")
    void createForUnknownCompany() {
        UUID companyId = UUID.randomUUID();
        given(companyRepository.findByIdForUpdate(companyId)).willReturn(Optional.empty());

        assertErrorCode(() -> productService.create(master(), companyId,
                new CreateProductCommand("생수", null, 1000L)), CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("MASTER는 남의 업체 상품 가격도 바꿀 수 있다")
    void updateByMaster() {
        Company company = company(UUID.randomUUID());
        Product product = product(company);
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        ProductResult result = productService.update(master(), company.getId(), product.getId(),
                new UpdateProductCommand(null, null, 1500L));

        assertThat(result.price()).isEqualTo(1500L);
        assertThat(result.name()).isEqualTo("생수");
    }

    @Test
    @DisplayName("경로의 업체 소속이 아닌 상품을 수정하면 PRODUCT_NOT_FOUND")
    void updateProductOfOtherCompany() {
        Product product = product(company(UUID.randomUUID()));
        Company other = company(UUID.randomUUID());
        given(companyRepository.findByIdForUpdate(other.getId())).willReturn(Optional.of(other));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        assertErrorCode(() -> productService.update(master(), other.getId(), product.getId(),
                new UpdateProductCommand("새 이름", null, null)), CompanyErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("업체가 이미 삭제됐으면 상품을 수정·삭제할 수 없고 상품은 읽지 않는다")
    void writeProductOfDeletedCompany() {
        UUID companyId = UUID.randomUUID(), productId = UUID.randomUUID();
        given(companyRepository.findByIdForUpdate(companyId)).willReturn(Optional.empty());

        assertErrorCode(() -> productService.update(master(), companyId, productId,
                new UpdateProductCommand("새 이름", null, null)), CompanyErrorCode.PRODUCT_NOT_FOUND);
        assertErrorCode(() -> productService.delete(master(), companyId, productId), CompanyErrorCode.PRODUCT_NOT_FOUND);
        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("업체 관리자가 상품을 삭제하면 논리 삭제한다")
    void deleteByManager() {
        UUID managerId = UUID.randomUUID();
        Company company = company(managerId);
        Product product = product(company);
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        productService.delete(user(managerId), company.getId(), product.getId());

        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("업체 관리자가 아니면 상품을 삭제할 수 없다")
    void deleteByOtherUser() {
        Company company = company(UUID.randomUUID());
        Product product = product(company);
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        assertErrorCode(() -> productService.delete(user(UUID.randomUUID()), company.getId(), product.getId()),
                CommonErrorCode.FORBIDDEN);
        assertThat(product.isDeleted()).isFalse();
    }
}
