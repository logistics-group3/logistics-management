package com.sparta.logistics.company.infrastructure.persistence;

import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** search는 ProductSearchRepositoryImpl이 구현한다(CompanyJpaRepository와 같은 방식). */
public interface ProductJpaRepository extends JpaRepository<Product, UUID>, ProductRepository, ProductSearchRepository {
}
