package com.sparta.logistics.hub.infrastructure.persistence;

import com.sparta.logistics.hub.domain.Hub;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** QueryDSL로 구현하는 검색 조각. Spring Data가 이름이 {@code Impl}로 끝나는 구현 클래스를 HubJpaRepository에 합친다. */
public interface HubQueryRepository {

    Page<Hub> search(String keyword, Pageable pageable);
}
