package com.sparta.logistics.company.application;

import java.util.UUID;

/**
 * hub-service 조회. 구현은 infrastructure/client의 Feign Adapter다.
 */
public interface HubReader {

    /** I-02로 허브가 있는지 확인한다. 없으면 hub-service의 404 HUB_NOT_FOUND가 그대로 올라온다. */
    void checkExists(UUID hubId);
}
