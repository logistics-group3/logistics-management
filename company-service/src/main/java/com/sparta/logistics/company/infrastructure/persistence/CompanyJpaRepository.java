package com.sparta.logistics.company.infrastructure.persistence;

import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Note: search는 CompanySearchRepository 조각(fragment)이 구현한다. Spring Data가 이름 규칙(인터페이스명 + Impl)으로
 * CompanySearchRepositoryImpl을 찾아 이 Repository의 search 호출을 그쪽으로 보낸다.
 */
public interface CompanyJpaRepository extends JpaRepository<Company, UUID>, CompanyRepository, CompanySearchRepository {

    // Note: C-05와 P-01이 같은 업체 행을 잠가 삭제와 상품 생성의 커밋 순서를 보장한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from Company company where company.id = :id")
    Optional<Company> findByIdForUpdate(@Param("id") UUID id);
}
