package com.sparta.logistics.delivery.application.assignment;

import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_HUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class ManagerAssignmentServiceTest {

    private static final UUID MANAGER_A = UUID.fromString("a0000000-0000-0000-0000-000000000000");
    private static final UUID MANAGER_B = UUID.fromString("b0000000-0000-0000-0000-000000000000");

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryQueryRepository deliveryQueryRepository;
    @Mock
    private DeliveryManagerReader deliveryManagerReader;
    private ManagerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ManagerAssignmentService(deliveryRepository, deliveryQueryRepository, deliveryManagerReader,
                TransactionOperations.withoutTransaction());
    }

    @Test
    @DisplayName("허브에 배송담당자 후보가 없으면 트랜잭션을 열지 않고 대기로 둔다")
    void noCandidates() {
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of());

        AssignmentResult result = service.assignPending(SUPPLIER_HUB);

        assertThat(result.outcome()).isEqualTo(AssignmentResult.Outcome.NO_CANDIDATES);
        then(deliveryRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 작업이 허브 잠금을 잡고 있으면 기다리지 않고 BUSY로 끝나며 배송을 건드리지 않는다")
    void busyWhenLockHeld() {
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of(MANAGER_A));
        given(deliveryRepository.tryLockHubAssignment(SUPPLIER_HUB)).willReturn(false);

        AssignmentResult result = service.assignPending(SUPPLIER_HUB);

        assertThat(result.outcome()).isEqualTo(AssignmentResult.Outcome.BUSY);
        then(deliveryRepository).should(never()).lockDeliveriesAwaitingManager(any(), anyInt());
    }

    @Test
    @DisplayName("대기 배송을 오래된 순으로 모아 담당 건수를 한 번 세고, 고를 때마다 건수를 올려 고르게 나눈다")
    void assignsBatchEvenly() {
        // 같은 허브 2구간 배송 3건 = 대기 구간 6개. A는 이미 1건, B는 0건이면 B, A, B, A, B, A 순으로 받아 4:3이 된다
        List<Delivery> deliveries = IntStream.range(0, 3).mapToObj(i -> unassignedSameHub(i)).toList();
        givenBatch(deliveries, List.of(MANAGER_A, MANAGER_B));
        given(deliveryQueryRepository.countActiveRoutes(anyCollection())).willReturn(List.of(new ActiveRouteCount(MANAGER_A, 1L)));

        AssignmentResult result = service.assignPending(SUPPLIER_HUB);

        assertThat(result.outcome()).isEqualTo(AssignmentResult.Outcome.ASSIGNED);
        assertThat(result.assignedRoutes()).isEqualTo(6);
        assertThat(result.hasMore()).isFalse();
        assertThat(deliveries.stream().flatMap(d -> d.getRoutes().stream()).map(DeliveryRoute::getManagerId))
                .containsExactly(MANAGER_B, MANAGER_A, MANAGER_B, MANAGER_A, MANAGER_B, MANAGER_A);
        assertThat(result.completed()).extracting(AssignedDelivery::deliveryId)
                .containsExactlyElementsOf(deliveries.stream().map(Delivery::getId).toList());
        then(deliveryQueryRepository).should().countActiveRoutes(anyCollection());
    }

    @Test
    @DisplayName("한 번에 처리할 양을 채우면 대기가 더 남았을 수 있다고 알린다")
    void hasMoreWhenBatchFull() {
        List<Delivery> deliveries = IntStream.range(0, ManagerAssignmentService.BATCH_SIZE)
                .mapToObj(i -> unassignedSameHub(i)).toList();
        givenBatch(deliveries, List.of(MANAGER_A));
        given(deliveryQueryRepository.countActiveRoutes(anyCollection())).willReturn(List.of());

        AssignmentResult result = service.assignPending(SUPPLIER_HUB);

        assertThat(result.hasMore()).isTrue();
    }

    private void givenBatch(List<Delivery> deliveries, List<UUID> candidates) {
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(candidates);
        given(deliveryRepository.tryLockHubAssignment(SUPPLIER_HUB)).willReturn(true);
        List<UUID> ids = deliveries.stream().map(Delivery::getId).toList();
        given(deliveryRepository.lockDeliveriesAwaitingManager(SUPPLIER_HUB, ManagerAssignmentService.BATCH_SIZE))
                .willReturn(ids);
        // DB는 순서를 보장하지 않는다. 서비스가 생성 시각으로 다시 정렬하는지 보려고 거꾸로 돌려준다
        given(deliveryRepository.findAllWithRoutesByIdIn(ids)).willReturn(reversed(deliveries));
    }

    private static List<Delivery> reversed(List<Delivery> deliveries) {
        List<Delivery> copy = new ArrayList<>(deliveries);
        Collections.reverse(copy);
        return copy;
    }

    private static Delivery unassignedSameHub(int order) {
        Delivery delivery = DeliveryFixture.unassigned(SUPPLIER_HUB, SUPPLIER_HUB);
        ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "createdAt", LocalDateTime.of(2026, 10, 19, 12, 0).plusSeconds(order));
        return delivery;
    }
}
