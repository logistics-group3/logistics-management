package com.sparta.logistics.company.infrastructure.persistence;

import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Note: search는 CompanySearchRepository 조각(fragment)이 구현한다. Spring Data가 이름 규칙(인터페이스명 + Impl)으로
 * CompanySearchRepositoryImpl을 찾아 이 Repository의 search 호출을 그쪽으로 보낸다.
 */
public interface CompanyJpaRepository extends JpaRepository<Company, UUID>, CompanyRepository, CompanySearchRepository {
}
