package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.HubRouteReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.RouteInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 생성 시도가 종료(I-13)된 뒤 지연된 I-09가 활성 배송을 만들지 못하는지 실제 PostgreSQL로 확인한다(이슈 #14).
 * 지연은 hub 경로 조회(원격 호출)를 래치로 붙잡아 만든다. hub·user 호출은 목이다.
 */
@AutoConfigureMockMvc
class DeliveryCreationClosureIntegrationTest extends PostgresIntegrationTest {

    private static final RouteInfo HUB_ROUTE = new RouteInfo(new BigDecimal("398.50"), 290);

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
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final UUID supplierHub = UUID.randomUUID();
    private final UUID receiverHub = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("삭제(I-13)가 대상 없이 먼저 끝난 뒤 지연된 생성(I-09)이 재개돼도 활성 배송이 남지 않는다")
    void delayedCreateAfterDeleteLeavesNoActiveDelivery() throws Exception {
        givenRemoteReaders();
        UUID orderId = UUID.randomUUID();
        CountDownLatch inRemoteCall = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        given(hubRouteReader.getExpectedRoute(any(), any())).willAnswer(invocation -> {
            inRemoteCall.countDown();
            resume.await(10, TimeUnit.SECONDS);
            return HUB_ROUTE;
        });

        Future<?> delayedCreate = executor.submit(() -> deliveryCreationService.create(command(orderId)));
        assertThat(inRemoteCall.await(10, TimeUnit.SECONDS)).as("생성이 원격 호출에서 멈춘다").isTrue();
        deliveryService.deleteByOrderId(orderId);
        resume.countDown();
        Throwable createOutcome = outcome(delayedCreate);

        assertThat(createOutcome).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(DeliveryErrorCode.DELIVERY_CREATION_CLOSED.code());
        assertThat(activeDeliveries(orderId)).isZero();
        assertThat(closures(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("저장 직전에 삭제가 끼어들어도(종료 표시 확인과 저장 사이) 저장은 삭제 커밋을 기다린 뒤 거절된다")
    void deleteInterleavedBeforeSaveIsSerialized() throws Exception {
        givenRemoteReaders();
        given(hubRouteReader.getExpectedRoute(any(), any())).willReturn(HUB_ROUTE);
        UUID orderId = UUID.randomUUID();
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commitDelete = new CountDownLatch(1);

        // 삭제를 바깥 트랜잭션에서 실행해 잠금과 종료 표시를 쥔 채 커밋을 미룬다. 커밋 전이라 생성의 첫 종료 표시 확인은 통과한다.
        Future<?> delete = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deliveryService.deleteByOrderId(orderId);
            deleted.countDown();
            await(commitDelete);
        }));
        assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> create = executor.submit(() -> deliveryCreationService.create(command(orderId)));
        try {
            create.get(1500, TimeUnit.MILLISECONDS);
            throw new AssertionError("저장이 삭제 커밋을 기다리지 않고 끝났다");
        } catch (java.util.concurrent.TimeoutException expectedBlockedByLock) {
            // 정상: 생성은 같은 orderId 잠금에서 기다린다
        } finally {
            commitDelete.countDown();
        }
        outcome(delete);
        Throwable createOutcome = outcome(create);

        assertThat(createOutcome).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(DeliveryErrorCode.DELIVERY_CREATION_CLOSED.code());
        assertThat(activeDeliveries(orderId)).isZero();
    }

    @Test
    @DisplayName("배송을 만든 뒤 삭제하면 배송은 삭제되고 종료 표시가 남아 같은 orderId로 다시 만들 수 없다. 삭제는 반복해도 같다")
    void deleteAfterCreateClosesAndIsIdempotent() throws Exception {
        givenRemoteReaders();
        given(hubRouteReader.getExpectedRoute(any(), any())).willReturn(HUB_ROUTE);
        UUID orderId = UUID.randomUUID();
        deliveryCreationService.create(command(orderId));
        assertThat(activeDeliveries(orderId)).isEqualTo(1);

        deliveryService.deleteByOrderId(orderId);
        deliveryService.deleteByOrderId(orderId);

        assertThat(activeDeliveries(orderId)).isZero();
        assertThat(closures(orderId)).isEqualTo(1);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> deliveryCreationService.create(command(orderId))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo(DeliveryErrorCode.DELIVERY_CREATION_CLOSED.code());
    }

    @Test
    @DisplayName("I-17은 배송 없음 NONE, 생성 후 ACTIVE, 삭제 후 CLOSED를 돌려주고 종료된 orderId의 I-09는 409다")
    void orderResultLookup() throws Exception {
        givenRemoteReaders();
        given(hubRouteReader.getExpectedRoute(any(), any())).willReturn(HUB_ROUTE);
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/internal/deliveries/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NONE"))
                .andExpect(jsonPath("$.deliveryId").doesNotExist());

        deliveryCreationService.create(command(orderId));
        mockMvc.perform(get("/internal/deliveries/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.status").value("PICKUP_WAITING"))
                .andExpect(jsonPath("$.deliveryId").exists());

        deliveryService.deleteByOrderId(orderId);
        mockMvc.perform(get("/internal/deliveries/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.deliveryId").doesNotExist());

        mockMvc.perform(post("/internal/deliveries").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(orderId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_CREATION_CLOSED"));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("커밋 신호가 오지 않았다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Throwable outcome(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private void givenRemoteReaders() {
        given(recipientReader.findSlackId(any())).willReturn(Optional.of("U02BCDEF3GH"));
        given(deliveryManagerReader.findIdsByHub(supplierHub)).willReturn(List.of(UUID.randomUUID()));
        given(deliveryManagerReader.findIdsByHub(receiverHub)).willReturn(List.of(UUID.randomUUID()));
    }

    private CreateDeliveryCommand command(UUID orderId) {
        return new CreateDeliveryCommand(orderId, UUID.randomUUID(), UUID.randomUUID(), supplierHub, receiverHub,
                "경기도 고양시 일산동구 중앙로 1234", "부산광역시 중구 자갈치해안로 52", "부산수산도매", UUID.randomUUID());
    }

    private int closures(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM p_delivery_creation_closures WHERE order_id = ?", Integer.class, orderId);
    }

    private int activeDeliveries(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM p_deliveries WHERE order_id = ? AND deleted_at IS NULL", Integer.class, orderId);
    }
}
