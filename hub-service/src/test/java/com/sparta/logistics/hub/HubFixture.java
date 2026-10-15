package com.sparta.logistics.hub;

import com.sparta.logistics.hub.domain.Hub;
import java.math.BigDecimal;

public final class HubFixture {

    private HubFixture() {
    }

    public static Hub hub(String name, String address) {
        return Hub.create(name, address, new BigDecimal("37.5665000"), new BigDecimal("126.9780000"));
    }
}
