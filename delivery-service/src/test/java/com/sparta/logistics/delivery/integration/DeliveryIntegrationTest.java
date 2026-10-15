package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.delivery.application.DeliveryCreationService;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryQueryService;
import com.sparta.logistics.delivery.application.DeliveryService;
import com.sparta.logistics.delivery.application.HubRouteReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.application.dto.DeliverySearchCondition;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.RouteInfo;
import com.sparta.logistics.delivery.domain.RouteStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 PostgreSQL로 확인해야 하는 배송 동작: orderId 멱등(유니크 제약·동시성), 담당 건수 쿼리, 배송담당자 조회 범위, 논리 삭제.
 * hub·user 호출은 목으로 바꾼다. 허브·담당자 ID는 테스트마다 새로 만들어 다른 테스트의 데이터와 섞이지 않게 한다.
 */
@AutoConfigureMockMvc
class DeliveryIntegrationTest extends PostgresIntegrationTest {

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
    private DeliveryQueryService deliveryQueryService;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DeliveryQueryRepository deliveryQueryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private final UUID supplierHub = UUID.randomUUID();
    private final UUID receiverHub = UUID.randomUUID();

    @Test
    @DisplayName("I-09는 새로 만들면 201, 같은 orderId로 다시 보내면 200과 같은 배송을 돌려주고 배송은 1건이다")
    void createIsIdempotent() throws Exception {
        UUID manager = UUID.randomUUID();
        givenManagers(Map.of(supplierHub, List.of(manager), receiverHub, List.of(manager)));
        CreateDeliveryCommand command = command(UUID.randomUUID());
        String body = objectMapper.writeValueAsString(command);

        String first = mockMvc.perform(post("/internal/deliveries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PICKUP_WAITING"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/internal/deliveries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryId").value(objectMapper.readTree(first).get("deliveryId").asText()));

        assertThat(countDeliveries(command.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 orderId로 동시에 I-09를 보내도 배송은 1건이고 모든 요청이 같은 배송을 받는다")
    void concurrentCreate() throws Exception {
        givenManagers(Map.of(supplierHub, List.of(UUID.randomUUID()), receiverHub, List.of(UUID.randomUUID())));
        CreateDeliveryCommand command = command(UUID.randomUUID());
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CreateDeliveryResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return deliveryCreationService.create(command);
            }));
        }
        start.countDown();

        List<CreateDeliveryResult> results = new ArrayList<>();
        for (Future<CreateDeliveryResult> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();

        assertThat(countDeliveries(command.orderId())).isEqualTo(1);
        assertThat(results).extracting(result -> result.delivery().deliveryId()).containsOnly(results.get(0).delivery().deliveryId());
        assertThat(results).filteredOn(CreateDeliveryResult::created).hasSize(1);
    }

    @Test
    @DisplayName("담당 건수는 WAITING·MOVING 구간만 세고 도착·취소·삭제된 구간은 세지 않는다")
    void countActiveRoutes() {
        UUID busy = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        givenManagers(Map.of(supplierHub, List.of(busy), receiverHub, List.of(idle)));
        // busy: 배송마다 집하·허브 이동 2구간, idle: 업체 배송 1구간
        UUID moving = create(UUID.randomUUID());     // busy 집하 ARRIVED, 허브 이동 MOVING → busy 1건, idle 1건
        create(UUID.randomUUID());                   // 대기: busy 2건, idle 1건
        UUID canceled = create(UUID.randomUUID());   // 취소 → 0건
        UUID deletedOrder = UUID.randomUUID();
        create(deletedOrder);                        // 삭제 → 0건

        DeliveryResponse result = deliveryQueryService.getDelivery(master(), moving);
        UUID pickup = result.routes().get(0).deliveryRouteId();
        UserContext master = master();
        deliveryService.changeRouteStatus(master, moving, pickup, new ChangeRouteStatusCommand(RouteStatus.MOVING, null, null));
        deliveryService.changeRouteStatus(master, moving, pickup, new ChangeRouteStatusCommand(RouteStatus.ARRIVED, null, null));
        deliveryService.changeRouteStatus(master, moving, result.routes().get(1).deliveryRouteId(),
                new ChangeRouteStatusCommand(RouteStatus.MOVING, null, null));
        deliveryService.cancel(canceled);
        deliveryService.deleteByOrderId(deletedOrder);

        assertThat(deliveryQueryRepository.countActiveRoutes(List.of(busy, idle, UUID.randomUUID())))
                .containsExactlyInAnyOrder(new ActiveRouteCount(busy, 3L), new ActiveRouteCount(idle, 2L));
    }

    @Test
    @DisplayName("배송담당자의 검색은 본인이 담당하는 구간이 있는 배송만, 한 배송에 담당 구간이 여럿이어도 한 번만 나온다")
    void deliveryManagerSearchScope() {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        givenManagers(Map.of(supplierHub, List.of(mine), receiverHub, List.of(other)));
        UUID myDelivery = create(UUID.randomUUID());   // 집하·허브 이동이 mine
        givenManagers(Map.of(supplierHub, List.of(other), receiverHub, List.of(other)));
        UUID otherDelivery = create(UUID.randomUUID());
        DeliverySearchCondition byHub = new DeliverySearchCondition(null, supplierHub, null, null, null, null, null, null);
        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<DeliverySummaryResult> mineOnly = deliveryQueryService.search(
                new UserContext(mine, "manager", Role.USER, true), byHub, page);
        Page<DeliverySummaryResult> all = deliveryQueryService.search(
                new UserContext(UUID.randomUUID(), "user", Role.USER, false), byHub, page);

        assertThat(mineOnly.getContent()).extracting(DeliverySummaryResult::deliveryId).containsExactly(myDelivery);
        assertThat(mineOnly.getTotalElements()).isEqualTo(1);
        assertThat(all.getContent()).extracting(DeliverySummaryResult::deliveryId)
                .containsExactlyInAnyOrder(myDelivery, otherDelivery);
    }

    @Test
    @DisplayName("I-13으로 삭제하면 배송과 구간이 함께 논리 삭제되어 조회되지 않고, 다시 삭제해도 성공한다")
    void softDelete() {
        givenManagers(Map.of(supplierHub, List.of(UUID.randomUUID()), receiverHub, List.of(UUID.randomUUID())));
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = create(orderId);

        deliveryService.deleteByOrderId(orderId);
        deliveryService.deleteByOrderId(orderId);

        assertThat(deliveryRepository.findByOrderId(orderId)).isEmpty();
        assertThat(deliveryRepository.findById(deliveryId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM p_delivery_routes WHERE delivery_id = ? AND deleted_at IS NOT NULL AND deleted_by = 'SYSTEM'",
                Integer.class, deliveryId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_by FROM p_deliveries WHERE delivery_id = ?", String.class, deliveryId)).isEqualTo("SYSTEM");
    }

    private UUID create(UUID orderId) {
        return deliveryCreationService.create(command(orderId)).delivery().deliveryId();
    }

    private void givenManagers(Map<UUID, List<UUID>> managersByHub) {
        given(hubRouteReader.getExpectedRoute(any(), any())).willReturn(HUB_ROUTE);
        given(recipientReader.findSlackId(any())).willReturn(Optional.of("U02BCDEF3GH"));
        managersByHub.forEach((hub, managers) -> given(deliveryManagerReader.findIdsByHub(hub)).willReturn(managers));
    }

    private CreateDeliveryCommand command(UUID orderId) {
        return new CreateDeliveryCommand(orderId, UUID.randomUUID(), UUID.randomUUID(), supplierHub, receiverHub,
                "경기도 고양시 일산동구 중앙로 1234", "부산광역시 중구 자갈치해안로 52", "부산수산도매", UUID.randomUUID());
    }

    private int countDeliveries(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM p_deliveries WHERE order_id = ?", Integer.class, orderId);
    }

    private static UserContext master() {
        return new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);
    }

}
