package com.sparta.logistics.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 마이그레이션이 Entity와 맞는지(ddl-auto=validate로 컨텍스트가 뜨는지)와,
 * Hibernate validate가 확인하지 않는 CHECK·FK 제약을 실제 PostgreSQL에서 확인한다.
 */
class CompanySchemaIntegrationTest extends PostgresIntegrationTest {

    private static final String AUDIT = "now(), 'SYSTEM', now(), 'SYSTEM'";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID insertCompany() {
        UUID companyId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO p_companies (company_id, name, address, hub_id, manager_id, "
                        + "created_at, created_by, updated_at, updated_by) VALUES (?, '스파르타 식품', '서울시', ?, ?, " + AUDIT + ")",
                companyId, UUID.randomUUID(), UUID.randomUUID());
        return companyId;
    }

    private void insertProduct(UUID companyId, long price) {
        jdbcTemplate.update("INSERT INTO p_products (product_id, name, price, company_id, "
                        + "created_at, created_by, updated_at, updated_by) VALUES (?, '생수', ?, ?, " + AUDIT + ")",
                UUID.randomUUID(), price, companyId);
    }

    @Test
    @DisplayName("가격이 0 이상이면 상품을 저장한다")
    void savesProductWithZeroPrice() {
        UUID companyId = insertCompany();

        insertProduct(companyId, 0);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM p_products WHERE company_id = ?", Integer.class, companyId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("가격이 음수면 CHECK 제약이 저장을 막는다")
    void rejectsNegativePrice() {
        UUID companyId = insertCompany();

        assertThatThrownBy(() -> insertProduct(companyId, -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_p_products_price");
    }

    @Test
    @DisplayName("없는 업체의 상품은 FK 제약이 저장을 막는다")
    void rejectsProductOfUnknownCompany() {
        assertThatThrownBy(() -> insertProduct(UUID.randomUUID(), 1000))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_p_products_company");
    }
}
