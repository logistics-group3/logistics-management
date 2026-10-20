package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.HubRouteReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.application.dto.UpdateRecipientCommand;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.domain.RouteInfo;
import com.sparta.logistics.delivery.domain.RouteStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 배송에 대한 변경 요청이 겹칠 때 상태가 어긋나지 않는지 실제 PostgreSQL로 확인한다(이슈 #11).
 *
 * 겹침 재현: 먼저 온 요청(A)을 테스트가 연 바깥 트랜잭션 안에서 호출하고 커밋을 미룬다. 서비스의 @Transactional은 바깥
 * 트랜잭션에 합류하므로 A는 "읽고 바꿨지만 아직 커밋하지 않은" 상태로 멈춘다. 그 사이 B를 보내 B가 끝나는지(잠금 없음) 기다리는지(잠금)를 본다.
 * 잠금이 있으면 B는 A가 커밋할 때까지 기다리고, A의 결과를 읽은 뒤 상태 규칙대로 판단한다.
 */
class DeliveryConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final RouteInfo HUB_ROUTE = new RouteInfo(new BigDecimal("398.50"), 290);
    private static final ChangeRouteStatusCommand MOVING = new ChangeRouteStatusCommand(RouteStatus.MOVING, null, null);
    private static final ChangeRouteStatusCommand ARRIVED = new ChangeRouteStatusCommand(RouteStatus.ARRIVED, null, null);
    /** B가 A를 기다리는지 판단하는 시간. 잠금이 없으면 이 안에 B가 끝난다. */
    private static final long BLOCK_CHECK_MILLIS = 1500;

    @MockitoBean
    private HubRouteReader hubRouteReader;
    @MockitoBean
    private DeliveryManagerReader deliveryManagerReader;
    @MockitoBean
    private RecipientReader recipientReader;

    @Autowired
    private DeliveryCreationService deliveryCreationService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    /** 같은 허브라 구간은 집하(1)·업체 배송(2) 두 개다. */
    private final UUID hub = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();

    @Test
    @DisplayName("집하 출발이 먼저 잠그면 생성 복구는 배송과 종료 표시를 변경하지 않는다")
    void recoveryAfterDepartureIsRejected() throws Exception {
        Delivery delivery = createDelivery();
        Race race = race(
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING),
                () -> deliveryService.closeCreation(delivery.orderId(), delivery.id()));
        assertThat(race.first()).isNull();
        assertThat(race.second()).isInstanceOf(BusinessException.class);
        assertThat(deliveryStatus(delivery.id())).isEqualTo("PICKING_UP");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM p_delivery_creation_closures WHERE order_id=?",
                Integer.class, delivery.orderId())).isZero();
    }

    @Test
    @DisplayName("생성 복구가 먼저 끝나면 뒤의 출발은 삭제된 배송을 시작하지 못한다")
    void departureAfterRecoveryIsRejected() throws Exception {
        Delivery delivery = createDelivery();
        Race race = race(
                () -> deliveryService.closeCreation(delivery.orderId(), delivery.id()),
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING));
        assertThat(race.first()).isNull();
        assertThat(race.second()).isInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM p_delivery_creation_closures WHERE order_id=?",
                Integer.class, delivery.orderId())).isEqualTo(1);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("재배정이 MOVING을 읽은 채 도착이 끼어들어도 도착 완료가 되돌아가지 않는다")
    void reassignDoesNotUndoArrival() throws Exception {
        Delivery delivery = createDelivery();
        UUID lastRoute = delivery.routeId(2);
        UUID newManager = UUID.randomUUID();
        given(deliveryManagerReader.findById(newManager)).willReturn(Optional.of(new DeliveryManager(newManager, hub)));
        advanceToLastMileMoving(delivery);

        Race race = race(
                () -> deliveryService.reassignManager(delivery.id(), lastRoute, newManager),
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), lastRoute, ARRIVED));

        assertThat(race.first()).isNull();
        assertThat(race.second()).isNull();
        assertThat(deliveryStatus(delivery.id())).isEqualTo("DELIVERED");
        assertThat(routeStatuses(delivery.id())).containsExactly("ARRIVED", "ARRIVED");
        assertThat(lastRouteManager(delivery.id())).isEqualTo(newManager);
    }

    @Test
    @DisplayName("집하 출발이 WAITING을 읽은 채 취소가 끼어들면 취소는 출발 결과를 보고 거절된다")
    void cancelAfterStartIsRejected() throws Exception {
        Delivery delivery = createDelivery();

        Race race = race(
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING),
                () -> deliveryService.cancel(delivery.id()));

        assertThat(race.first()).isNull();
        assertThat(race.second()).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION.code());
        assertThat(deliveryStatus(delivery.id())).isEqualTo("PICKING_UP");
        assertThat(routeStatuses(delivery.id())).containsExactly("MOVING", "WAITING");
    }

    @Test
    @DisplayName("취소가 먼저 끝나면 집하 출발은 취소된 상태를 보고 거절된다")
    void startAfterCancelIsRejected() throws Exception {
        Delivery delivery = createDelivery();

        Race race = race(
                () -> deliveryService.cancel(delivery.id()),
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING));

        assertThat(race.first()).isNull();
        assertThat(race.second()).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION.code());
        assertThat(deliveryStatus(delivery.id())).isEqualTo("CANCELED");
        assertThat(routeStatuses(delivery.id())).containsExactly("CANCELED", "CANCELED");
    }

    @Test
    @DisplayName("삭제가 먼저 끝나면 출발 요청은 삭제된 배송을 찾지 못한다")
    void startAfterDeleteIsNotFound() throws Exception {
        Delivery delivery = createDelivery();

        Race race = race(
                () -> deliveryService.deleteByOrderId(delivery.orderId()),
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING));

        assertThat(race.first()).isNull();
        assertThat(race.second()).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(DeliveryErrorCode.DELIVERY_NOT_FOUND.code());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM p_deliveries WHERE delivery_id = ?", Boolean.class, delivery.id())).isTrue();
    }

    @Test
    @DisplayName("수령인 수정과 집하 출발이 겹쳐도 바뀐 수령인과 PICKING_UP 상태가 함께 남는다")
    void updateRecipientAndStartAreBothKept() throws Exception {
        Delivery delivery = createDelivery();

        Race race = race(
                () -> deliveryService.updateRecipient(master(), delivery.id(), new UpdateRecipientCommand(null, "변경된 수령인")),
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING));

        // 잠금이 없으면 수정이 읽은 PICKUP_WAITING으로 배송 행 전체를 덮어써 출발 결과가 사라진다
        assertThat(race.first()).isNull();
        assertThat(race.second()).isNull();
        assertThat(deliveryStatus(delivery.id())).isEqualTo("PICKING_UP");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT recipient_name FROM p_deliveries WHERE delivery_id = ?", String.class, delivery.id()))
                .isEqualTo("변경된 수령인");
    }

    @Test
    @DisplayName("같은 구간 출발이 두 번 겹치면 한 번만 성공하고 두 번째는 INVALID_STATUS_TRANSITION")
    void duplicateStartIsRejected() throws Exception {
        Delivery delivery = createDelivery();

        Race race = race(
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING),
                () -> deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING));

        assertThat(race.first()).isNull();
        assertThat(race.second()).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(CommonErrorCode.INVALID_STATUS_TRANSITION.code());
        assertThat(routeStatuses(delivery.id())).containsExactly("MOVING", "WAITING");
    }

    @Test
    @DisplayName("다른 배송의 변경은 잠긴 배송을 기다리지 않고 진행한다")
    void otherDeliveryIsIndependent() throws Exception {
        Delivery locked = createDelivery();
        Delivery other = createDelivery();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = executor.submit(() -> inTransaction(() -> {
            deliveryService.changeRouteStatus(master(), locked.id(), locked.routeId(1), MOVING);
            held.countDown();
            await(release);
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> independent = executor.submit(
                () -> deliveryService.changeRouteStatus(master(), other.id(), other.routeId(1), MOVING));
        independent.get(BLOCK_CHECK_MILLIS * 4, TimeUnit.MILLISECONDS);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertThat(deliveryStatus(other.id())).isEqualTo("PICKING_UP");
    }

    /**
     * first를 바깥 트랜잭션에서 실행해 커밋 직전에 멈추고, 그 사이 second를 보낸다.
     * second가 끝나면 잠금이 없는 것이고, 기다리면 잠금이다. 어느 쪽이든 first를 풀어 두 요청의 결과를 돌려준다.
     */
    private Race race(Runnable first, Runnable second) throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> firstFuture = executor.submit(() -> inTransaction(() -> {
            first.run();
            firstDone.countDown();
            await(release);
        }));
        assertThat(firstDone.await(10, TimeUnit.SECONDS)).as("first 요청이 읽고 바꾼 뒤 멈춘다").isTrue();

        Future<?> secondFuture = executor.submit(second);
        try {
            secondFuture.get(BLOCK_CHECK_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException blockedByLock) {
            // second가 first의 커밋을 기다리는 정상 경로
        } finally {
            release.countDown();
        }
        return new Race(outcome(firstFuture), outcome(secondFuture));
    }

    private Throwable outcome(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release가 오지 않았다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Delivery createDelivery() {
        given(hubRouteReader.getExpectedRoute(any(), any())).willReturn(HUB_ROUTE);
        given(recipientReader.findSlackId(any())).willReturn(Optional.of("U02BCDEF3GH"));
        given(deliveryManagerReader.findIdsByHub(hub)).willReturn(List.of(manager));
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = deliveryCreationService.create(new CreateDeliveryCommand(orderId, UUID.randomUUID(), UUID.randomUUID(),
                hub, hub, "경기도 고양시 일산동구 중앙로 1234", "부산광역시 중구 자갈치해안로 52", "부산수산도매", UUID.randomUUID()))
                .delivery().deliveryId();
        List<UUID> routeIds = jdbcTemplate.queryForList(
                "SELECT delivery_route_id FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence", UUID.class, deliveryId);
        return new Delivery(deliveryId, orderId, routeIds);
    }

    /** 집하를 도착까지 마치고 업체 배송 구간을 MOVING으로 만든다. */
    private void advanceToLastMileMoving(Delivery delivery) {
        deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), MOVING);
        deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(1), ARRIVED);
        deliveryService.changeRouteStatus(master(), delivery.id(), delivery.routeId(2), MOVING);
    }

    private String deliveryStatus(UUID deliveryId) {
        return jdbcTemplate.queryForObject("SELECT status FROM p_deliveries WHERE delivery_id = ?", String.class, deliveryId);
    }

    private List<String> routeStatuses(UUID deliveryId) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence", String.class, deliveryId);
    }

    private UUID lastRouteManager(UUID deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT manager_id FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence DESC LIMIT 1", UUID.class, deliveryId);
    }

    private static UserContext master() {
        return new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);
    }

    /** 먼저 보낸 요청과 끼어든 요청의 예외(성공이면 null) */
    private record Race(Throwable first, Throwable second) {
    }

    private record Delivery(UUID id, UUID orderId, List<UUID> routeIds) {

        /** 순번은 1부터 */
        UUID routeId(int sequence) {
            return routeIds.get(sequence - 1);
        }
    }
}
