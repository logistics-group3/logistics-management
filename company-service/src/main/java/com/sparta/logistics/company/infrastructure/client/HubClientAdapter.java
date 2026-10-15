package com.sparta.logistics.company.infrastructure.client;

import com.sparta.logistics.company.application.HubReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HubClientAdapter implements HubReader {

    private final HubClient hubClient;

    /** 404는 FeignErrorDecoder가 BusinessException(HUB_NOT_FOUND)으로 바꾼다. 응답이 오면 존재하는 허브다. */
    @Override
    public void checkExists(UUID hubId) {
        hubClient.getHub(hubId);
    }
}
