package com.sparta.logistics.delivery;

import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.ManagerAssigner;
import com.sparta.logistics.delivery.domain.RouteInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** 단위 테스트용 배송. 저장하지 않으므로 ID는 ReflectionTestUtils로 채운다. */
public final class DeliveryFixture {

    public static final UUID SUPPLIER_HUB = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID RECEIVER_HUB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    /** 공급업체 허브 소속 배송담당자(집하·허브 이동) */
    public static final UUID SUPPLIER_MANAGER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    /** 요청업체 허브 소속 배송담당자(업체 배송) */
    public static final UUID RECEIVER_MANAGER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    public static final RouteInfo HUB_ROUTE = new RouteInfo(new BigDecimal("398.50"), 290);

    private DeliveryFixture() {
    }

    /** 공급업체 허브 ≠ 요청업체 허브: 집하·허브 이동·업체 배송 3구간 */
    public static Delivery threeRoutes() {
        return withIds(create(SUPPLIER_HUB, RECEIVER_HUB));
    }

    /** 같은 허브: 집하·업체 배송 2구간 */
    public static Delivery twoRoutes() {
        return withIds(create(SUPPLIER_HUB, SUPPLIER_HUB));
    }

    public static ManagerAssigner assigner() {
        return new ManagerAssigner(
                Map.of(SUPPLIER_HUB, List.of(SUPPLIER_MANAGER), RECEIVER_HUB, List.of(RECEIVER_MANAGER)), Map.of());
    }

    private static Delivery create(UUID departureHub, UUID destinationHub) {
        return Delivery.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), departureHub, destinationHub,
                "경기도 고양시 일산동구 중앙로 1234", "부산광역시 중구 자갈치해안로 52", "부산수산도매", "U02BCDEF3GH",
                departureHub.equals(destinationHub) ? null : HUB_ROUTE, assigner());
    }

    private static Delivery withIds(Delivery delivery) {
        ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
        delivery.getRoutes().forEach(route -> ReflectionTestUtils.setField(route, "id", UUID.randomUUID()));
        return delivery;
    }
}
