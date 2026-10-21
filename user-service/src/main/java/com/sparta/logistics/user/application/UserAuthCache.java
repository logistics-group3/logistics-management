package com.sparta.logistics.user.application;

import java.util.UUID;

/** Gateway 권한 재확인 캐시 삭제. 구현은 infrastructure에 있고, 캐시가 로컬(Gateway 메모리)이면 아무것도 하지 않는다. */
public interface UserAuthCache {

    void evict(UUID userId);
}
