package com.sparta.logistics.user.application.dto;

import java.util.UUID;

/** 배송담당자 지정에 필요한 허브 정보. 소속명(affiliationName)에 허브명을 넣는다. */
public record HubInfo(UUID hubId, String name) {
}
