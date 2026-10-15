package com.sparta.logistics.company.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * domain이 필요로 하는 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다.
 * 검색(C-02)은 infrastructure 단계에서 QueryDSL 구현과 함께 추가한다.
 */
public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);
}
