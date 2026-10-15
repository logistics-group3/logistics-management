package com.sparta.logistics.user.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * domain이 필요로 하는 저장소 기능만 선언한다. 구현은 infrastructure의 Spring Data 인터페이스가 맡는다.
 */
public interface UserRepository {

    /** 저장하고 바로 INSERT를 실행한다. 유니크 제약 위반을 호출한 메서드 안에서 잡기 위해 쓴다. */
    User saveAndFlush(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    /**
     * 허브 소속 배송담당자 목록(I-14). 탈퇴자는 @SQLRestriction으로 빠진다.
     * Note: @SQLRestriction이 붙이는 {@code deleted_at IS NULL}이 부분 인덱스 idx_p_users_hub_id(V1)의 조건과 같아서,
     * PostgreSQL이 이 조회에 그 인덱스를 쓸 수 있다. 부분 인덱스는 쿼리 조건이 인덱스 조건을 포함할 때만 쓰인다.
     */
    List<User> findAllByHubIdAndDeliveryManagerTrue(UUID hubId);

    /** 논리 삭제된 사용자까지 포함해 username 사용 여부를 확인한다. */
    boolean existsByUsernameIncludingDeleted(String username);
}
