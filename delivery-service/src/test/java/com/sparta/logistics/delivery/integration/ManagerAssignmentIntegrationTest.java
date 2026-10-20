package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.DeliveryQueryService;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.HubRouteReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.application.assignment.AssignmentTrigger;
import com.sparta.logistics.delivery.application.assignment.DeliveryConfirmationService;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.domain.RouteStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 구간 담당자 비동기 배치 배정을 실제 PostgreSQL로 확인한다(결정 로그 27). 배정은 주문 확정(I-19) 뒤에만 한다(결정 로그 29).
 *
 * Note: 테스트 설정은 배정을 부른 스레드에서 바로 실행한다(delivery.assignment.async=false). 그래도 같은 허브의 배정은 신호 합치기와
 * DB try-lock으로 한 번에 하나만 돈다. 그래서 동시에 만든 배송 중 일부는 다른 스레드가 이어서 배정한다.
 * Context 재사용을 위해 목과 애너테이션 구성을 DeliveryIntegrationTest와 같게 둔다.
 */
@AutoConfigureMockMvc
class ManagerAssignmentIntegrationTest extends PostgresIntegrationTest {

    private static final int MANAGERS = 5;
    private static final int REQUESTS = 50;
    /** I-01 응답 시간. 동기 배정 때는 이 틈 때문에 같은 건수를 읽었다(결정 로그 27). */
    private static final long RECIPIENT_LOOKUP_MILLIS = 50;
    private static final ChangeRouteStatusCommand MOVING = new ChangeRouteStatusCommand(RouteStatus.MOVING, null, null);

    @MockitoBean
    private HubRouteReader hubRouteReader;
    @MockitoBean
    private DeliveryManagerReader deliveryManagerReader;
    @MockitoBean
    private RecipientReader recipientReader;

    @Autowired
    private DeliveryCreationService deliveryCreationService;
    @Autowired
    private DeliveryConfirmationService deliveryConfirmationService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private DeliveryQueryService deliveryQueryService;
    @Autowired
    private DeliveryQueryRepository deliveryQueryRepository;
    @Autowired
    private AssignmentTrigger assignmentTrigger;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 허브에 배송 50건이 동시에 들어와도 모두 배정되고 배송담당자 5명의 담당 건수 차이가 1건 이하다")
    void concurrentCreationIsAssignedEvenly() throws Exception {
        UUID hub = UUID.randomUUID();
        List<UUID> managers = IntStream.range(0, MANAGERS).mapToObj(i -> UUID.randomUUID()).toList();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(managers);
        given(recipientReader.findSlackId(any())).willAnswer(invocation -> {
            Thread.sleep(RECIPIENT_LOOKUP_MILLIS);
            return Optional.empty();
        });

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            results.add(executor.submit(() -> {
                start.await();
                CreateDeliveryCommand command = sameHubCommand(hub);
                deliveryCreationService.create(command);
                return deliveryConfirmationService.confirm(command.orderId());
            }));
        }
        long startedAt = System.nanoTime();
        start.countDown();
        for (Future<?> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        // 같은 허브 배송은 구간 2개(집하, 업체 배송)라 모두 100구간이다. 고르게 나뉘면 1명당 20구간이다.
        Map<UUID, Long> counts = deliveryQueryRepository.countActiveRoutes(managers).stream()
                .collect(Collectors.toMap(ActiveRouteCount::managerId, ActiveRouteCount::count));
        LongSummaryStatistics stats = managers.stream().mapToLong(m -> counts.getOrDefault(m, 0L)).summaryStatistics();
        System.out.printf("[배정 편차] 담당 건수 %s, 최대-최소 %d건, 대기 %d구간, 동시 %d건 처리 %dms%n",
                managers.stream().map(m -> counts.getOrDefault(m, 0L)).toList(), stats.getMax() - stats.getMin(),
                pendingRoutes(hub), REQUESTS, elapsedMillis);

        assertThat(pendingRoutes(hub)).isZero();
        assertThat(stats.getSum()).isEqualTo(REQUESTS * 2L);
        assertThat(stats.getMax() - stats.getMin()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("후보가 없으면 주문은 성공하고 구간은 배정 대기로 남는다. 출발은 거절되고, 후보가 생기면 다음 배정에서 채워진다")
    void noCandidatesLeavesRoutesPending() {
        UUID hub = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of());
        given(recipientReader.findSlackId(any())).willReturn(Optional.empty());

        UUID deliveryId = createConfirmed(hub);
        UUID pickup = routeIds(deliveryId).get(0);

        assertThat(pendingRoutes(hub)).isEqualTo(2);
        assertThat(deliveryQueryService.getNotificationRecipients(deliveryId).managerIds()).isEmpty();
        assertThatThrownBy(() -> deliveryService.changeRouteStatus(master(), deliveryId, pickup, MOVING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION);

        // 주기 작업이 깨우는 것과 같다
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of(manager));
        assignmentTrigger.request(Set.of(hub));

        assertThat(pendingRoutes(hub)).isZero();
        assertThat(deliveryQueryService.getNotificationRecipients(deliveryId).managerIds()).containsExactly(manager);
        deliveryService.changeRouteStatus(master(), deliveryId, pickup, MOVING);
    }

    @Test
    @DisplayName("후보가 없어 대기인 구간은 MASTER가 D-04로 직접 배정할 수 있다")
    void masterAssignsPendingRoute() {
        UUID hub = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of());
        given(deliveryManagerReader.findById(manager)).willReturn(Optional.of(new DeliveryManager(manager, hub)));
        given(recipientReader.findSlackId(any())).willReturn(Optional.empty());
        UUID deliveryId = createConfirmed(hub);
        UUID pickup = routeIds(deliveryId).get(0);

        deliveryService.reassignManager(deliveryId, pickup, manager);

        assertThat(pendingRoutes(hub)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT assigned_at IS NOT NULL FROM p_delivery_routes WHERE delivery_route_id = ?",
                Boolean.class, pickup)).isTrue();
        deliveryService.changeRouteStatus(master(), deliveryId, pickup, MOVING);
    }

    @Test
    @DisplayName("주문 확정 전에는 배정 작업이 돌아도 담당자를 넣지 않고, 확정하면 그때 배정한다")
    void assignsOnlyAfterConfirmation() {
        UUID hub = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of(manager));
        given(recipientReader.findSlackId(any())).willReturn(Optional.empty());
        CreateDeliveryCommand command = sameHubCommand(hub);
        UUID deliveryId = deliveryCreationService.create(command).delivery().deliveryId();

        assignmentTrigger.request(Set.of(hub));

        assertThat(pendingRoutes(hub)).isEqualTo(2);
        assertThat(deliveryQueryRepository.findHubsAwaitingManager()).doesNotContain(hub);
        assertThat(deliveryQueryRepository.findUnconfirmedOrderIds(LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1), 1000)).contains(command.orderId());

        deliveryConfirmationService.confirm(command.orderId());

        assertThat(pendingRoutes(hub)).isZero();
        assertThat(deliveryQueryService.getNotificationRecipients(deliveryId).managerIds()).containsExactly(manager);
    }

    @Test
    @DisplayName("후보 0명이 확정 뒤 기준 시간을 넘으면 수동 지정으로 넘기고, 이후 후보가 생겨도 자동 배정은 건너뛰며 D-04로만 채운다")
    void overdueWithoutCandidatesMovesToManualAssignment() {
        UUID hub = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of());
        given(deliveryManagerReader.findById(manager)).willReturn(Optional.of(new DeliveryManager(manager, hub)));
        given(recipientReader.findSlackId(any())).willReturn(Optional.empty());
        UUID recent = createConfirmed(hub);
        UUID overdue = createConfirmed(hub);
        // 확정 뒤 기준 시간(테스트 설정 60초)이 지난 배송으로 만든다
        jdbcTemplate.update("UPDATE p_deliveries SET confirmed_at = now() - interval '2 minutes' WHERE delivery_id = ?", overdue);

        assignmentTrigger.request(Set.of(hub));

        assertThat(manualRequestedAt(overdue)).isNotNull();
        assertThat(manualRequestedAt(recent)).isNull();

        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of(manager));
        assignmentTrigger.request(Set.of(hub));

        assertThat(routeManagers(recent)).containsOnly(manager);
        assertThat(routeManagers(overdue)).containsOnlyNulls();
        assertThat(deliveryQueryRepository.findHubsAwaitingManager()).doesNotContain(hub);
        for (UUID routeId : routeIds(overdue)) {
            deliveryService.reassignManager(overdue, routeId, manager);
        }
        assertThat(routeManagers(overdue)).containsOnly(manager);
    }

    private UUID createConfirmed(UUID hub) {
        CreateDeliveryCommand command = sameHubCommand(hub);
        UUID deliveryId = deliveryCreationService.create(command).delivery().deliveryId();
        deliveryConfirmationService.confirm(command.orderId());
        return deliveryId;
    }

    private Object manualRequestedAt(UUID deliveryId) {
        return jdbcTemplate.queryForObject("SELECT manual_assignment_requested_at FROM p_deliveries WHERE delivery_id = ?",
                Object.class, deliveryId);
    }

    private List<UUID> routeManagers(UUID deliveryId) {
        return jdbcTemplate.queryForList("SELECT manager_id FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence",
                UUID.class, deliveryId);
    }

    private int pendingRoutes(UUID hub) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM p_delivery_routes
                WHERE managing_hub_id = ? AND manager_id IS NULL AND status = 'WAITING' AND deleted_at IS NULL
                """, Integer.class, hub);
    }

    private List<UUID> routeIds(UUID deliveryId) {
        return jdbcTemplate.queryForList(
                "SELECT delivery_route_id FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence", UUID.class, deliveryId);
    }

    private static CreateDeliveryCommand sameHubCommand(UUID hub) {
        return new CreateDeliveryCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), hub, hub,
                "서울특별시 송파구 송파대로 55", "서울특별시 강남구 테헤란로 152", "강남상회", UUID.randomUUID());
    }

    private static UserContext master() {
        return new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);
    }
}
