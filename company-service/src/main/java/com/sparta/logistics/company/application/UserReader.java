package com.sparta.logistics.company.application;

import java.util.UUID;

/**
 * user-service 조회. 구현은 infrastructure/client의 Feign Adapter다.
 */
public interface UserReader {

    /** I-01로 사용자가 있는지 확인한다. 없거나 탈퇴했으면 user-service의 404 USER_NOT_FOUND가 그대로 올라온다. */
    void checkExists(UUID userId);
}
