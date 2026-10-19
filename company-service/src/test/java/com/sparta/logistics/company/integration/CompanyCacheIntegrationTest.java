package com.sparta.logistics.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.company.application.CompanyQueryService;
import com.sparta.logistics.company.application.CompanyService;
import com.sparta.logistics.company.application.ProductQueryService;
import com.sparta.logistics.company.application.ProductService;
import com.sparta.logistics.company.application.dto.CreateProductCommand;
import com.sparta.logistics.company.application.dto.UpdateCompanyCommand;
import com.sparta.logistics.company.application.dto.UpdateProductCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * company.cache.type=local에서 내부 조회(I-07, I-08)가 캐시되고, 수정·삭제가 커밋 뒤 키를 지우는지 확인한다(성능 측정 항목 C).
 * Note: 삭제를 커밋 뒤로 미루는 동작을 보려면 실제로 커밋해야 해서 테스트 트랜잭션으로 감싸지 않는다. 데이터는 매번 새 ID로 만든다.
 */
@TestPropertySource(properties = "company.cache.type=local")
class CompanyCacheIntegrationTest extends PostgresIntegrationTest {

    @Autowired private CompanyRepository companies;
    @Autowired private CompanyService companyService;
    @Autowired private CompanyQueryService companyQueryService;
    @Autowired private ProductService productService;
    @Autowired private ProductQueryService productQueryService;
    @Autowired private JdbcTemplate jdbc;

    private final UserContext master = new UserContext(UUID.randomUUID(), "cache-master", Role.MASTER, false);

    @Test
    @DisplayName("I-08 상품 조회는 캐시되고, 상품 수정이 키를 지워 다음 조회에 새 값이 나온다")
    void productCachedAndEvictedOnUpdate() {
        Company company = company();
        UUID productId = productService.create(master, company.getId(), new CreateProductCommand("캐시 상품", null, 1000L)).productId();
        productQueryService.getInternalProduct(productId);

        jdbc.update("UPDATE p_products SET price = 9999 WHERE product_id = ?", productId);
        assertThat(productQueryService.getInternalProduct(productId).price()).as("캐시에서 읽는다").isEqualTo(1000L);

        productService.update(master, company.getId(), productId, new UpdateProductCommand(null, null, 2000L));
        assertThat(productQueryService.getInternalProduct(productId).price()).as("수정이 키를 지웠다").isEqualTo(2000L);
    }

    @Test
    @DisplayName("I-07 업체 조회는 업체 수정이 키를 지운다")
    void companyEvictedOnUpdate() {
        Company company = company();
        companyQueryService.getInternalCompany(company.getId());

        companyService.update(master, company.getId(), new UpdateCompanyCommand("바뀐 업체명", null, null, null));

        assertThat(companyQueryService.getInternalCompany(company.getId()).name()).isEqualTo("바뀐 업체명");
    }

    @Test
    @DisplayName("업체를 삭제하면 업체와 소속 상품의 캐시가 함께 지워져 둘 다 404다")
    void companyDeletionEvictsProducts() {
        Company company = company();
        UUID productId = productService.create(master, company.getId(), new CreateProductCommand("삭제될 상품", null, 1000L)).productId();
        companyQueryService.getInternalCompany(company.getId());
        productQueryService.getInternalProduct(productId);

        companyService.delete(master, company.getId());

        assertThatThrownBy(() -> companyQueryService.getInternalCompany(company.getId()))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND);
        assertThatThrownBy(() -> productQueryService.getInternalProduct(productId))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CompanyErrorCode.PRODUCT_NOT_FOUND);
    }

    private Company company() {
        return companies.save(Company.create("캐시 테스트 업체", "테스트 주소", UUID.randomUUID(), master.userId()));
    }
}
