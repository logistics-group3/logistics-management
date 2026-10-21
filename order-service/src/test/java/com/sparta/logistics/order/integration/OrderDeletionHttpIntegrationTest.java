package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.RecordedRequest;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"
})
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "spring.jpa.open-in-view=false", "slack.webhook-url=",
        "spring.cloud.openfeign.client.config.default.connectTimeout=1000",
        "spring.cloud.openfeign.client.config.default.readTimeout=200"
})
class OrderDeletionHttpIntegrationTest extends PostgresIntegrationTest {
    private static final HttpStubServer STUB = new HttpStubServer();
    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry registry) {
        for (String client : List.of("order-company", "order-delivery-create", "order-delivery-delete", "order-delivery-read",
                "order-delivery-cancel", "order-delivery-cancel-revert")) {
            registry.add("spring.cloud.openfeign.client.config." + client + ".url", STUB::url);
        }
    }
    @AfterAll static void stopStub() { STUB.close(); }
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderRepository orders;
    @Autowired private OrderCreationRepository creations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private OrderNotificationSender notification;
    private final UUID owner = UUID.randomUUID();
    private final Map<UUID, Order> fixtures = new HashMap<>();

    @BeforeEach
    void setUp() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        http.getRestTemplate().setRequestFactory(factory);
        STUB.reset(this::normal);
    }

    @AfterEach
    void cleanUp() {
        try {
            verifyNoInteractions(notification);
            assertThat(STUB.requests("POST", "/")).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE order_schema.p_order_deletions DROP CONSTRAINT IF EXISTS ck_test_delete_http_failure");
            for (UUID id : fixtures.keySet()) {
                jdbc.update("DELETE FROM order_schema.p_order_deletions WHERE order_id=?", id);
                jdbc.update("DELETE FROM order_schema.p_order_creations WHERE order_id=?", id);
                jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("취소·배송 완료 DELETE는 200 data null이며 조회에서 숨기고 Audit와 내부 헤더를 유지한다")
    void deletesEligibleOrder(boolean canceled) throws Exception {
        Order order = seed(canceled);
        ResponseEntity<String> response = delete(order.getId(), "MASTER");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(mapper.readTree(response.getBody()).get("data").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM order_schema.p_orders WHERE order_id=?", String.class, order.getId())).isEqualTo("delete-user");
        assertThat(jdbc.queryForObject("SELECT created_by || ':' || updated_by FROM order_schema.p_order_deletions WHERE order_id=?", String.class, order.getId())).isEqualTo("delete-user:delete-user");
        assertThat(STUB.requests("GET", "/internal/deliveries/")).hasSize(canceled ? 0 : 1);
        List<RecordedRequest> calls = STUB.requests("DELETE", "/internal/deliveries/orders/");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).header(UserHeaders.USER_ID)).isEqualTo(owner.toString());
        assertThat(calls.get(0).header(UserHeaders.ROLE)).isEqualTo("MASTER");
        ResponseEntity<String> read = http.exchange("/api/v1/orders/" + order.getId(), HttpMethod.GET, new HttpEntity<>(headers("USER")), String.class);
        error(read, 404, "ORDER_NOT_FOUND");
        error(delete(order.getId(), "MASTER"), 404, "ORDER_NOT_FOUND");
        assertThat(STUB.requests("DELETE", "/")).hasSize(1);
    }

    @Test
    @DisplayName("일반 사용자·미인증·없는 주문·잘못된 UUID는 원격 호출 전에 거부한다")
    void rejectsUnauthorizedAndInvalidRequests() throws Exception {
        Order order = seed(true);
        error(delete(order.getId(), "USER"), 403, "FORBIDDEN");
        error(delete(order.getId(), null), 401, "UNAUTHORIZED");
        error(delete(UUID.randomUUID(), "MASTER"), 404, "ORDER_NOT_FOUND");
        error(http.exchange("/api/v1/orders/invalid", HttpMethod.DELETE, new HttpEntity<>(headers("MASTER")), String.class), 400, "VALIDATION_ERROR");
        assertThat(STUB.requests("GET", "/")).isEmpty();
        assertThat(STUB.requests("DELETE", "/")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PICKUP_WAITING", "PICKING_UP", "HUB_WAITING", "HUB_MOVING", "DEST_HUB_ARRIVED", "OUT_FOR_DELIVERY", "CANCELED"})
    @DisplayName("ORDERED 주문의 배송이 DELIVERED가 아니면 409이며 삭제하지 않는다")
    void refusesUndeliveredOrder(String state) throws Exception {
        Order order = seed(false);
        STUB.reset(request -> lookup(order, state));
        error(delete(order.getId(), "MASTER"), 409, "ORDER_NOT_DELETABLE");
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(countRecords(order)).isZero();
        assertThat(STUB.requests("DELETE", "/")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"status\":\"DELIVERED\"}", "wrong-delivery", "wrong-order", "unknown-status", "null-status"})
    @DisplayName("I-10의 누락·참조 불일치·알 수 없는 상태는 503이며 삭제 허가로 쓰지 않는다")
    void validatesDeliveryResponse(String kind) throws Exception {
        Order order = seed(false);
        String body = kind;
        if (kind.startsWith("wrong-")) body = json(Map.of("deliveryId", kind.equals("wrong-delivery") ? UUID.randomUUID() : order.getDeliveryId(),
                "orderId", kind.equals("wrong-order") ? UUID.randomUUID() : order.getId(), "status", "DELIVERED"));
        if (kind.equals("unknown-status")) body = json(Map.of("deliveryId", order.getDeliveryId(), "orderId", order.getId(), "status", "NEW_STATUS"));
        if (kind.equals("null-status")) body = json(Map.of("deliveryId", order.getDeliveryId(), "orderId", order.getId()));
        String responseBody = body;
        STUB.reset(request -> Reply.json(200, responseBody));
        error(delete(order.getId(), "MASTER"), 503, "SERVICE_UNAVAILABLE");
        assertThat(countRecords(order)).isZero();
        assertThat(STUB.requests("DELETE", "/")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"404", "500", "timeout"})
    @DisplayName("I-10 거절·장애는 기록 없이 종료하며 통신 타임아웃만 3회 시도한다")
    void handlesLookupFailure(String mode) throws Exception {
        Order order = seed(false);
        STUB.reset(request -> mode.equals("timeout") ? lookup(order, "DELIVERED").delayed(800)
                : failure(Integer.parseInt(mode), mode.equals("404") ? "DELIVERY_NOT_FOUND" : "INTERNAL_SERVER_ERROR"));
        error(delete(order.getId(), "MASTER"), mode.equals("404") ? 404 : 503, mode.equals("404") ? "DELIVERY_NOT_FOUND" : "SERVICE_UNAVAILABLE");
        assertThat(STUB.requests("GET", "/")).hasSize(mode.equals("timeout") ? 3 : 1);
        assertThat(STUB.requests("DELETE", "/")).isEmpty();
        assertThat(countRecords(order)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("I-13 5xx·타임아웃은 한 번만 호출하고 다음 DELETE는 I-10 없이 같은 삭제를 마친다")
    void resumesUncertainDelete(boolean timeout) throws Exception {
        Order order = seed(false);
        STUB.reset(request -> request.method().equals("DELETE")
                ? timeout ? Reply.json(204, "").delayed(800) : failure(500, "INTERNAL_SERVER_ERROR") : normal(request));
        error(delete(order.getId(), "MASTER"), 503, "SERVICE_UNAVAILABLE");
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(STUB.requests("DELETE", "/")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM order_schema.p_order_deletions WHERE order_id=?", String.class, order.getId())).isEqualTo("RECOVERY_REQUIRED");
        STUB.reset(request -> request.method().equals("DELETE") ? Reply.json(204, "") : failure(404, "DELIVERY_NOT_FOUND"));
        assertThat(delete(order.getId(), "MASTER").getStatusCode().value()).isEqualTo(200);
        assertThat(STUB.requests("GET", "/")).isEmpty();
        assertThat(STUB.requests("DELETE", "/")).hasSize(1);
        assertThat(orders.findById(order.getId())).isEmpty();
        assertThat(countRecords(order)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("시작·완료 기록 저장 오류는 500이며 원격 삭제 실행 여부와 로컬 롤백을 구분한다")
    void handlesRecordFailure(boolean completion) throws Exception {
        Order order = seed(true);
        jdbc.execute("ALTER TABLE order_schema.p_order_deletions ADD CONSTRAINT ck_test_delete_http_failure CHECK (order_id <> '" + order.getId() + "'::uuid OR status <> '" + (completion ? "COMPLETED" : "PROCESSING") + "')");
        error(delete(order.getId(), "MASTER"), 500, "INTERNAL_SERVER_ERROR");
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(STUB.requests("DELETE", "/")).hasSize(completion ? 1 : 0);
        if (completion) {
            jdbc.execute("ALTER TABLE order_schema.p_order_deletions DROP CONSTRAINT ck_test_delete_http_failure");
            STUB.reset(request -> request.method().equals("DELETE") ? Reply.json(204, "") : failure(404, "DELIVERY_NOT_FOUND"));
            assertThat(delete(order.getId(), "MASTER").getStatusCode().value()).isEqualTo(200);
            assertThat(STUB.requests("GET", "/")).isEmpty();
            assertThat(STUB.requests("DELETE", "/")).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 202})
    @DisplayName("I-13의 명세와 다른 2xx는 삭제 완료로 오인하지 않고 로컬 삭제를 롤백한다")
    void rejectsUnconfirmedDeleteStatus(int status) throws Exception {
        Order order = seed(true);
        STUB.reset(request -> Reply.json(status, "{}"));
        error(delete(order.getId(), "MASTER"), 503, "SERVICE_UNAVAILABLE");
        assertThat(orders.findById(order.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT status FROM order_schema.p_order_deletions WHERE order_id=?", String.class, order.getId()))
                .isEqualTo("RECOVERY_REQUIRED");
    }

    @Test
    @DisplayName("삭제 후에도 O-01 같은 멱등키는 최초 생성 응답을 반환하고 배송을 새로 만들지 않는다")
    void preservesCreationReplayAfterDeletion() throws Exception {
        Order order = seed(false);
        CreateOrderCommand command = new CreateOrderCommand(order.getSupplierCompanyId(), order.getReceiverCompanyId(), order.getProductId(), order.getQuantity(), order.getRequestNote());
        transactions.executeWithoutResult(status -> {
            OrderCreation creation = OrderCreation.start(UUID.randomUUID(), order.getId(), owner, command.identity("created-order-key"));
            creation.completeWithSnapshot(order);
            creations.saveAndFlush(creation);
        });
        assertThat(delete(order.getId(), "MASTER").getStatusCode().value()).isEqualTo(200);
        HttpHeaders headers = headers("USER"); headers.set("Idempotency-Key", "created-order-key"); headers.set("Content-Type", "application/json");
        ResponseEntity<String> replay = http.postForEntity("/api/v1/orders", new HttpEntity<>(json(command), headers), String.class);
        assertThat(replay.getStatusCode().value()).isEqualTo(201);
        assertThat(mapper.readTree(replay.getBody()).path("data").path("orderId").asText()).isEqualTo(order.getId().toString());
        assertThat(mapper.readTree(replay.getBody()).path("data").path("status").asText()).isEqualTo("ORDERED");
        assertThat(STUB.requests("POST", "/")).isEmpty();
    }

    @Test
    @DisplayName("Gateway가 사용하는 OpenAPI에 O-06 DELETE와 200 응답이 노출된다")
    void documentsDelete() throws Exception {
        JsonNode operation = mapper.readTree(http.getForEntity("/v3/api-docs", String.class).getBody()).path("paths").path("/api/v1/orders/{orderId}").path("delete");
        assertThat(operation.path("summary").asText()).isEqualTo("O-06 주문 삭제");
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    private Order seed(boolean canceled) {
        Order order = Order.create(UUID.randomUUID(), owner, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "상품", 12000L, 2, UUID.randomUUID(), "요청사항");
        if (canceled) order.cancel();
        Order saved = transactions.execute(status -> orders.saveAndFlush(order));
        fixtures.put(saved.getId(), saved);
        return saved;
    }
    private ResponseEntity<String> delete(UUID id, String role) {
        return http.exchange("/api/v1/orders/" + id, HttpMethod.DELETE, new HttpEntity<>(headers(role)), String.class);
    }
    private HttpHeaders headers(String role) {
        HttpHeaders headers = new HttpHeaders();
        if (role != null) {
            headers.set(UserHeaders.USER_ID, owner.toString()); headers.set(UserHeaders.USERNAME, "delete-user");
            headers.set(UserHeaders.ROLE, role); headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        }
        return headers;
    }
    private Reply normal(RecordedRequest request) {
        if (request.method().equals("DELETE")) return Reply.json(204, "");
        return fixtures.values().stream().filter(order -> request.path().equals("/internal/deliveries/" + order.getDeliveryId()))
                .findFirst().map(order -> lookup(order, "DELIVERED")).orElseGet(() -> failure(404, "DELIVERY_NOT_FOUND"));
    }
    private Reply lookup(Order order, String status) { return Reply.json(200, json(Map.of("deliveryId", order.getDeliveryId(), "orderId", order.getId(), "status", status))); }
    private Reply failure(int status, String code) { return Reply.json(status, json(Map.of("code", code, "message", "테스트 오류"))); }
    private int countRecords(Order order) { return jdbc.queryForObject("SELECT count(*) FROM order_schema.p_order_deletions WHERE order_id=?", Integer.class, order.getId()); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void error(ResponseEntity<String> response, int status, String code) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo(code);
    }
}
