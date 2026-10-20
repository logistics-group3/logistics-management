package com.sparta.logistics.company.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.company.application.CompanyCacheNames;
import com.sparta.logistics.company.application.ProductQueryService;
import com.sparta.logistics.company.application.ProductService;
import com.sparta.logistics.company.application.dto.CreateProductCommand;
import com.sparta.logistics.company.application.dto.InternalProductResult;
import com.sparta.logistics.company.application.dto.UpdateProductCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

/** company.cache.type=redis에서 내부 조회 결과가 JSON으로 저장·복원되고, 상품 수정이 Redis 키를 지우는지 확인한다(측정 조건 C1). */
@TestPropertySource(properties = "company.cache.type=redis")
class CompanyRedisCacheIntegrationTest extends PostgresIntegrationTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private CompanyRepository companies;
    @Autowired private ProductService productService;
    @Autowired private ProductQueryService productQueryService;
    @Autowired private CacheManager cacheManager;

    private final UserContext master = new UserContext(UUID.randomUUID(), "redis-master", Role.MASTER, false);

    @Test
    @DisplayName("상품 조회 결과가 Redis에 저장되고 상품 수정이 키를 지운다")
    void roundTripAndEvict() {
        Company company = companies.save(Company.create("Redis 테스트 업체", "테스트 주소", UUID.randomUUID(), master.userId()));
        UUID productId = productService.create(master, company.getId(), new CreateProductCommand("Redis 상품", null, 1000L)).productId();

        InternalProductResult first = productQueryService.getInternalProduct(productId);
        assertThat(cacheManager.getCache(CompanyCacheNames.INTERNAL_PRODUCT).get(productId, InternalProductResult.class)).isEqualTo(first);

        productService.update(master, company.getId(), productId, new UpdateProductCommand(null, null, 3000L));
        assertThat(cacheManager.getCache(CompanyCacheNames.INTERNAL_PRODUCT).get(productId)).isNull();
        assertThat(productQueryService.getInternalProduct(productId).price()).isEqualTo(3000L);
    }
}
