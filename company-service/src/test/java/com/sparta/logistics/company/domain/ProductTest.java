package com.sparta.logistics.company.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductTest {

    private static Company companyWithId(UUID id) {
        Company company = Company.create("스파르타 식품", "서울시 강남구 1", UUID.randomUUID(), UUID.randomUUID());
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    @Test
    @DisplayName("수정 값이 null이면 기존 값을 유지한다")
    void updateKeepsNullFields() {
        Product product = Product.create(companyWithId(UUID.randomUUID()), "생수", "500ml", 1000L);

        product.update(null, null, 1200L);

        assertThat(product.getName()).isEqualTo("생수");
        assertThat(product.getDescription()).isEqualTo("500ml");
        assertThat(product.getPrice()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("소속 업체 ID와 같을 때만 그 업체의 상품이다")
    void belongsTo() {
        UUID companyId = UUID.randomUUID();
        Product product = Product.create(companyWithId(companyId), "생수", null, 1000L);

        assertThat(product.belongsTo(companyId)).isTrue();
        assertThat(product.belongsTo(UUID.randomUUID())).isFalse();
    }
}
