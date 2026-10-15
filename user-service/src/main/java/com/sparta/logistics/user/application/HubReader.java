package com.sparta.logistics.user.application;

import com.sparta.logistics.user.application.dto.HubInfo;
import java.util.UUID;

/** hub-service 허브 조회(I-02). 없으면 404 HUB_NOT_FOUND, 응답을 받지 못하면 503 SERVICE_UNAVAILABLE이 그대로 올라온다. */
public interface HubReader {

    HubInfo getHub(UUID hubId);
}
