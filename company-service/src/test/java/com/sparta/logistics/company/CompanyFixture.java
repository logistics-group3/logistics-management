package com.sparta.logistics.company;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.Product;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** 서비스 단위 테스트용 데이터. 저장하지 않은 Entity에 ID를 직접 넣는다. */
public final class CompanyFixture {

    private CompanyFixture() {
    }

    public static UserContext user(UUID userId) {
        return new UserContext(userId, "user01", Role.USER, false);
    }

    public static UserContext master() {
        return new UserContext(UUID.randomUUID(), "master", Role.MASTER, false);
    }

    public static Company company(UUID managerId) {
        Company company = Company.create("스파르타 식품", "서울시 강남구 1", UUID.randomUUID(), managerId);
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
        return company;
    }

    public static Product product(Company company) {
        Product product = Product.create(company, "생수", "500ml", 1000L);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        return product;
    }
}
