package com.sparta.logistics.hub.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * domain이 필요로 하는 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다.
 */
public interface HubRepository {

    Optional<Hub> findById(UUID hubId);

    /** 이름 또는 주소에 keyword가 포함된 허브를 찾는다. keyword가 비어 있으면 전체. 정렬은 Pageable을 따른다. */
    Page<Hub> search(String keyword, Pageable pageable);
}
