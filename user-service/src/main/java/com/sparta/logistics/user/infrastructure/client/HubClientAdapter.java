package com.sparta.logistics.user.infrastructure.client;

import com.sparta.logistics.user.application.HubReader;
import com.sparta.logistics.user.application.dto.HubInfo;
import com.sparta.logistics.user.infrastructure.client.dto.HubResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Note: application은 HubReader 인터페이스만 알고 Feign을 모른다. 테스트에서 HubReader를 목으로 바꾸면 hub-service 없이
 * U-07을 검증할 수 있고, 호출 방식(Feign, 메시지 등)을 바꿔도 application 코드는 그대로다.
 */
@Component
@RequiredArgsConstructor
public class HubClientAdapter implements HubReader {

    private final HubClient hubClient;

    @Override
    public HubInfo getHub(UUID hubId) {
        HubResponse hub = hubClient.getHub(hubId);
        return new HubInfo(hub.hubId(), hub.name());
    }
}
