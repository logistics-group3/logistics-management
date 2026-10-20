package com.sparta.logistics.delivery.application.assignment;

import static com.sparta.logistics.delivery.DeliveryFixture.SUPPLIER_HUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.DeliveryFixture;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import java.time.Duration;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class ManagerAssignmentServiceTest {

    private static final UUID MANAGER_A = UUID.fromString("a0000000-0000-0000-0000-000000000000");
    private static final UUID MANAGER_B = UUID.fromString("b0000000-0000-0000-0000-000000000000");
    private static final Duration MANUAL_AFTER = Duration.ofSeconds(60);

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryQueryRepository deliveryQueryRepository;
    @Mock
    private DeliveryManagerReader deliveryManagerReader;
    @Mock
    private AssignmentNotifier assignmentNotifier;
    private ManagerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ManagerAssignmentService(deliveryRepository, deliveryQueryRepository, deliveryManagerReader,
                TransactionOperations.withoutTransaction(), assignmentNotifier, new ManualAssignmentPolicy(MANUAL_AFTER));
    }

    @Test
    @DisplayName("후보가 0명이어도 확정 뒤 기준 시간이 지난 배송이 없으면 대기로 두고 아무에게도 알리지 않는다")
    void noCandidatesWithinGracePeriod() {
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of());
        given(deliveryRepository.tryLockHubAssignment(SUPPLIER_HUB)).willReturn(true);
        given(deliveryRepository.lockDeliveriesAwaitingManagerConfirmedBefore(eq(SUPPLIER_HUB), any(), anyInt())).willReturn(List.of());

        AssignmentResult result = service.assignPending(SUPPLIER_HUB);

        assertThat(result.outcome()).isEqualTo(AssignmentResult.Outcome.NO_CANDIDATES);
        assertThat(result.manualRequired()).isEmpty();
        then(assignmentNotifier).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("후보가 0명인 상태가 확정 뒤 기준 시간을 넘으면 그 배송을 수동 지정으로 넘기고 커밋 뒤 order에 알린다")
    void noCandidatesPastGracePeriodRequestsManualAssignment() {
        Delivery overdue = unassignedSameHub(0);
        overdue.confirm();
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willReturn(List.of());
        given(deliveryRepository.tryLockHubAssignment(SUPPLIER_HUB)).willReturn(true);
        ArgumentCaptor<LocalDateTime> confirmedBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        given(deliveryRepository.lockDeliveriesAwaitingManagerConfirmedBefore(eq(SUPPLIER_HUB), confirmedBefore.capture(), anyInt()))
                .willReturn(List.of(overdue.getId()));
        given(deliveryRepository.findAllWithRoutesByIdIn(List.of(overdue.getId()))).willReturn(List.of(overdue));
        LocalDateTime before = LocalDateTime.now();

        AssignmentResult result = service.assignPending(SUPPLIER_HUB);

        assertThat(confirmedBefore.getValue()).isBetween(before.minus(MANUAL_AFTER), LocalDateTime.now().minus(MANUAL_AFTER));
        assertThat(overdue.getManualAssignmentRequestedAt()).isNotNull();
        List<AssignedDelivery> expected = List.of(new AssignedDelivery(overdue.getId(), overdue.getOrderId()));
        assertThat(result.manualRequired()).isEqualTo(expected);
        then(assignmentNotifier).should().notifyManualRequired(expected);
        then(assignmentNotifier).should(never()).notifyAssigned(any());
    }

    @Test
    @DisplayName("user 장애로 후보를 읽지 못하면 예외를 올리고 배송을 수동 지정으로 넘기지 않는다")
    void userFailureDoesNotRequestManualAssignment() {
        given(deliveryManagerReader.findIdsByHub(SUPPLIER_HUB)).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.assignPending(SUPPLIER_HUB)).isInstanceOf(BusinessException.class);

        then(deliveryRepository).shouldHaveNoInteractions();
        then(assignmentNotifier).shouldHaveNoInteractions();
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
        then(assignmentNotifier).should().notifyAssigned(result.completed());
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
