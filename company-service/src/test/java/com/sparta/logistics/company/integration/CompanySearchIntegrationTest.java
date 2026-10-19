package com.sparta.logistics.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.InvalidSortException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.CompanySearchCondition;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import com.sparta.logistics.company.domain.ProductSearchCondition;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * QueryDSL 검색 조건, 정렬 허용 목록, 논리 삭제 제외를 실제 PostgreSQL에서 확인한다.
 * 컨테이너를 테스트 클래스끼리 공유하므로 매번 새 hubId·managerId로 데이터를 구분한다.
 */
class CompanySearchIntegrationTest extends PostgresIntegrationTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "name"));

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private ProductRepository productRepository;

    private Company saveCompany(String name, UUID hubId, UUID managerId) {
        return companyRepository.save(Company.create(name, "서울시", hubId, managerId));
    }

    @Test
    @DisplayName("업체 검색은 이름 부분 일치와 허브 조건을 함께 적용한다")
    void searchCompaniesByNameAndHub() {
        UUID hubId = UUID.randomUUID();
        saveCompany("스파르타 식품", hubId, UUID.randomUUID());
        saveCompany("스파르타 전자", hubId, UUID.randomUUID());
        saveCompany("스파르타 식품", UUID.randomUUID(), UUID.randomUUID());

        Page<Company> page = companyRepository.search(new CompanySearchCondition("식품", hubId, null), FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Company::getName).containsExactly("스파르타 식품");
    }

    @Test
    @DisplayName("논리 삭제한 업체는 검색되지 않는다")
    void searchExcludesDeletedCompany() {
        UUID managerId = UUID.randomUUID();
        saveCompany("남는 업체", UUID.randomUUID(), managerId);
        Company deleted = saveCompany("지운 업체", UUID.randomUUID(), managerId);
        deleted.softDelete("tester");
        companyRepository.save(deleted);

        Page<Company> page = companyRepository.search(new CompanySearchCondition(null, null, managerId), FIRST_PAGE);

        assertThat(page.getContent()).extracting(Company::getName).containsExactly("남는 업체");
    }

    @Test
    @DisplayName("허용하지 않은 필드로 정렬하면 InvalidSortException이 난다")
    void rejectsUnknownSort() {
        PageRequest byAddress = PageRequest.of(0, 10, Sort.by("address"));

        assertThatThrownBy(() -> companyRepository.search(new CompanySearchCondition(null, null, null), byAddress))
                .isInstanceOf(InvalidSortException.class);
    }

    @Test
    @DisplayName("상품 검색의 hubId는 소속 업체의 관리 허브로 거르고 가격 순으로 정렬한다")
    void searchProductsByCompanyHub() {
        UUID hubId = UUID.randomUUID();
        Company inHub = saveCompany("허브 안 업체", hubId, UUID.randomUUID());
        Company otherHub = saveCompany("다른 허브 업체", UUID.randomUUID(), UUID.randomUUID());
        productRepository.save(Product.create(inHub, "비싼 생수", null, 3000L));
        productRepository.save(Product.create(inHub, "싼 생수", null, 1000L));
        productRepository.save(Product.create(otherHub, "다른 생수", null, 2000L));

        Page<Product> page = productRepository.search(new ProductSearchCondition(null, null, hubId),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price")));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("싼 생수", "비싼 생수");
    }

    @Test
    @DisplayName("업체 ID로 소속 상품을 모두 찾는다")
    void findAllByCompanyId() {
        Company company = saveCompany("스파르타 식품", UUID.randomUUID(), UUID.randomUUID());
        productRepository.save(Product.create(company, "생수", null, 1000L));
        productRepository.save(Product.create(company, "주스", null, 2000L));

        assertThat(productRepository.findAllByCompanyId(company.getId())).hasSize(2);
    }
}
