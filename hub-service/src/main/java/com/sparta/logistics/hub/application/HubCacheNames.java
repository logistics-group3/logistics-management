package com.sparta.logistics.hub.application;

/** 조회 캐시 이름. 저장소 설정은 infrastructure/config/HubCacheConfig에 있다. */
public final class HubCacheNames {

    public static final String HUB = "hub";
    public static final String INTERNAL_HUB = "internalHub";
    public static final String HUB_ROUTE = "hubRoute";
    public static final String INTERNAL_HUB_ROUTE = "internalHubRoute";

    private HubCacheNames() {
    }
}
