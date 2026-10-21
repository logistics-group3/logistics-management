package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.RecordedRequest;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** HTTP Controller → 실제 Feign → 주문 DB. delivery 응답만 로컬 HTTP 서버로 대체한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"
})
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "spring.jpa.open-in-view=false", "slack.webhook-url=",
        "spring.cloud.openfeign.client.config.default.connectTimeout=1000",
        "spring.cloud.openfeign.client.config.default.readTimeout=200"
})
class OrderCancellationHttpIntegrationTest extends PostgresIntegrationTest {

    private static final HttpStubServer STUB = new HttpStubServer();

    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry registry) {
        for (String client : List.of("order-company", "order-delivery-create", "order-delivery-delete",
                "order-delivery-cancel", "order-delivery-cancel-revert")) {
            registry.add("spring.cloud.openfeign.client.config." + client + ".url", STUB::url);
        }
    }

    @AfterAll
    static void closeStub() { STUB.close(); }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private OrderNotificationSender notification;

    private final UUID ownerId = UUID.randomUUID();
    private final List<UUID> fixtureIds = new ArrayList<>();

    @BeforeEach
    void configureHttp() {
        // Note: HttpURLConnection의 POST/401 처리에서 오류 본문이 사라질 수 있어 JDK HttpClient로 응답 계약을 검증한다.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        http.getRestTemplate().setRequestFactory(factory);
        STUB.reset(this::success);
    }

    @AfterEach
    void cleanUp() {
        try {
            verifyNoInteractions(notification);
            assertThat(STUB.requests("GET", "/")).isEmpty();
            assertThat(STUB.requests("DELETE", "/")).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE order_schema.p_orders DROP CONSTRAINT IF EXISTS ck_test_cancel_http_failure");
            fixtureIds.forEach(id -> {
                jdbc.update("DELETE FROM order_schema.p_order_cancellations WHERE order_id=?", id);
                jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MASTER"})
    @DisplayName("취소 API는 본인·MASTER 권한으로 실제 Feign을 한 번 호출하고 명세 응답과 Audit를 저장한다")
    void cancelsThroughHttpAndFeign(String role) throws Exception {
        Order order = seed();
        ResponseEntity<String> response = cancel(order.getId(), role.equals("USER") ? ownerId : UUID.randomUUID(), role);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.path("message").asText()).isEqualTo("주문이 취소되었습니다.");
        assertThat(body.path("data").size()).isEqualTo(2);
        assertThat(body.path("data").path("orderId").asText()).isEqualTo(order.getId().toString());
        assertThat(body.path("data").path("status").asText()).isEqualTo("CANCELED");
        Order updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(updated.getUpdatedBy()).isEqualTo("cancel-user");
        assertThat(updated.getTotalPrice()).isEqualTo(600_000L);
        assertThat(record(order.getId(), "status")).isEqualTo("COMPLETED");
        assertThat(record(order.getId(), "created_by")).isEqualTo("cancel-user");
        RecordedRequest request = exactRequests(order, "/cancel").get(0);
        assertThat(exactRequests(order, "/cancel")).hasSize(1);
        assertThat(request.body()).isEmpty();
        assertThat(request.header(UserHeaders.USERNAME)).isEqualTo("cancel-user");
        assertThat(request.header(UserHeaders.ROLE)).isEqualTo(role);
        assertThat(exactRequests(order, "/cancel-revert")).isEmpty();
    }

    @Test
    @DisplayName("타인 주문은 진행 기록이나 원격 호출 없이 403이다")
    void rejectsOtherOwner() throws Exception {
        Order order = seed();

        assertError(cancel(order.getId(), UUID.randomUUID(), "USER"), 403, "FORBIDDEN");
        assertNoAttemptOrRemoteCall(order.getId());
    }

    @Test
    @DisplayName("없는 주문과 논리 삭제된 주문은 404이며 배송을 호출하지 않는다")
    void rejectsMissingAndDeleted() throws Exception {
        Order order = seed();
        jdbc.update("UPDATE order_schema.p_orders SET deleted_at=now(), deleted_by='fixture' WHERE order_id=?", order.getId());

        assertError(cancel(order.getId()), 404, "ORDER_NOT_FOUND");
        assertError(cancel(UUID.randomUUID()), 404, "ORDER_NOT_FOUND");
        assertNoAttemptOrRemoteCall(order.getId());
    }

    @Test
    @DisplayName("이미 취소된 주문의 재요청은 409이며 배송 취소를 다시 호출하지 않는다")
    void rejectsAlreadyCanceled() throws Exception {
        Order order = seed();
        jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED' WHERE order_id=?", order.getId());

        assertError(cancel(order.getId()), 409, "INVALID_STATUS_TRANSITION");
        assertNoAttemptOrRemoteCall(order.getId());
    }

    @Test
    @DisplayName("사용자 정보가 없는 취소는 401이다")
    void requiresAuthentication() throws Exception {
        Order order = seed();

        assertError(http.postForEntity("/api/v1/orders/" + order.getId() + "/cancel", null, String.class), 401, "UNAUTHORIZED");
        assertNoAttemptOrRemoteCall(order.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409})
    @DisplayName("배송의 명확한 거절 코드는 그대로 전달하고 시도를 ABORTED로 종료한다")
    void propagatesDeliveryRejection(int status) throws Exception {
        Order order = seed();
        String code = status == 409 ? "INVALID_STATUS_TRANSITION" : "DELIVERY_NOT_FOUND";
        STUB.reset(request -> Reply.json(status, "{\"code\":\"" + code + "\",\"message\":\"거절\"}"));

        assertError(cancel(order.getId()), status, code);
        assertThat(record(order.getId(), "status")).isEqualTo("ABORTED");
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(exactRequests(order, "/cancel")).hasSize(1);
        assertThat(exactRequests(order, "/cancel-revert")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("배송 5xx는 재시도·자동 원복 없이 503과 복구 기록으로 남긴다")
    void retainsUnknownServerFailure(int status) throws Exception {
        Order order = seed();
        STUB.reset(request -> Reply.json(status, "{}"));

        assertError(cancel(order.getId()), 503, "SERVICE_UNAVAILABLE");
        assertRecovery(order, "DELIVERY_RESULT_UNKNOWN");
        assertThat(exactRequests(order, "/cancel")).hasSize(1);
        assertThat(exactRequests(order, "/cancel-revert")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":\"PICKUP_WAITING\"}", "not-json"})
    @DisplayName("200이어도 취소 성공을 확인할 수 없는 본문은 503·복구 대기다")
    void rejectsMalformedSuccess(String body) throws Exception {
        Order order = seed();
        STUB.reset(request -> Reply.json(200, body));

        assertError(cancel(order.getId()), 503, "SERVICE_UNAVAILABLE");
        assertRecovery(order, "DELIVERY_RESULT_UNKNOWN");
        assertThat(exactRequests(order, "/cancel-revert")).isEmpty();
    }

    @Test
    @DisplayName("취소가 타임아웃 뒤 늦게 성공해도 새 요청·원복을 자동 실행하지 않는다")
    void protectsAgainstLateCancellationCompletion() throws Exception {
        Order order = seed();
        AtomicBoolean remoteCompleted = new AtomicBoolean();
        STUB.reset(request -> {
            try { Thread.sleep(600); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            remoteCompleted.set(true);
            return success(request);
        });

        assertError(cancel(order.getId()), 503, "SERVICE_UNAVAILABLE");
        assertRecovery(order, "DELIVERY_RESULT_UNKNOWN");
        await().atMost(Duration.ofSeconds(3)).untilTrue(remoteCompleted);
        assertError(cancel(order.getId()), 409, "INVALID_STATUS_TRANSITION");
        assertThat(exactRequests(order, "/cancel")).hasSize(1);
        assertThat(exactRequests(order, "/cancel-revert")).isEmpty();
    }

    @Test
    @DisplayName("원복 통신 실패는 최대 3회 시도하며 성공하면 ABORTED로 종료한다")
    void retriesOnlyRevertTransportFailure() throws Exception {
        Order order = seed();
        failLocalUpdate(order);
        AtomicInteger reverts = new AtomicInteger();
        STUB.reset(request -> request.path().endsWith("/cancel-revert") && reverts.incrementAndGet() < 3
                ? success(request).delayed(600) : success(request));

        assertError(cancel(order.getId()), 500, "INTERNAL_SERVER_ERROR");
        assertThat(record(order.getId(), "status")).isEqualTo("ABORTED");
        assertThat(exactRequests(order, "/cancel")).hasSize(1);
        assertThat(exactRequests(order, "/cancel-revert")).hasSize(3);
    }

    @Test
    @DisplayName("원복의 5xx는 추가 재시도 없이 최초 저장 실패 500과 복구 기록을 유지한다")
    void doesNotRetryRevertServerFailure() throws Exception {
        Order order = seed();
        failLocalUpdate(order);
        STUB.reset(request -> request.path().endsWith("/cancel-revert") ? Reply.json(503, "{}") : success(request));

        assertError(cancel(order.getId()), 500, "INTERNAL_SERVER_ERROR");
        assertRecovery(order, "REVERT_FAILED");
        assertThat(exactRequests(order, "/cancel-revert")).hasSize(1);
    }

    @Test
    @DisplayName("원복 통신이 세 번 모두 실패하면 새 취소를 막고 복구 대상으로 남긴다")
    void recordsExhaustedRevertAttempts() throws Exception {
        Order order = seed();
        failLocalUpdate(order);
        STUB.reset(request -> request.path().endsWith("/cancel-revert") ? success(request).delayed(600) : success(request));

        assertError(cancel(order.getId()), 500, "INTERNAL_SERVER_ERROR");
        assertRecovery(order, "REVERT_FAILED");
        assertThat(exactRequests(order, "/cancel-revert")).hasSize(3);
        assertError(cancel(order.getId()), 409, "INVALID_STATUS_TRANSITION");
        assertThat(exactRequests(order, "/cancel")).hasSize(1);
    }

    @Test
    @DisplayName("OpenAPI에 O-05 취소 경로와 주문 ID·상태 응답만 표시한다")
    void documentsCancellation() throws Exception {
        JsonNode doc = mapper.readTree(http.getForEntity("/v3/api-docs", String.class).getBody());
        assertThat(doc.path("paths").path("/api/v1/orders/{orderId}/cancel").path("post").path("summary").asText()).isEqualTo("O-05 주문 취소");
        assertThat(doc.path("components").path("schemas").path("CancelOrderResponse").path("properties").size()).isEqualTo(2);
    }

    private Reply success(RecordedRequest request) {
        return Reply.json(200, request.path().endsWith("/cancel-revert") ? "{\"status\":\"PICKUP_WAITING\"}" : "{\"status\":\"CANCELED\"}");
    }

    private void failLocalUpdate(Order order) {
        jdbc.execute("ALTER TABLE order_schema.p_orders ADD CONSTRAINT ck_test_cancel_http_failure CHECK (order_id <> '"
                + order.getId() + "'::uuid OR status <> 'CANCELED')");
    }

    private void assertRecovery(Order order, String reason) {
        assertThat(record(order.getId(), "status")).isEqualTo("RECOVERY_REQUIRED");
        assertThat(record(order.getId(), "failure_reason")).isEqualTo(reason);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    private void assertNoAttemptOrRemoteCall(UUID orderId) {
        assertThat(STUB.requests("POST", "/")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_order_cancellations WHERE order_id=?", Integer.class, orderId)).isZero();
    }

    private List<RecordedRequest> exactRequests(Order order, String suffix) {
        String path = "/internal/deliveries/" + order.getDeliveryId() + suffix;
        return STUB.requests("POST", path).stream().filter(request -> request.path().equals(path)).toList();
    }

    private String record(UUID orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM order_schema.p_order_cancellations WHERE order_id=?", String.class, orderId);
    }

    private ResponseEntity<String> cancel(UUID orderId) { return cancel(orderId, ownerId, "USER"); }

    private ResponseEntity<String> cancel(UUID orderId, UUID userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(UserHeaders.USER_ID, userId.toString());
        headers.set(UserHeaders.USERNAME, "cancel-user");
        headers.set(UserHeaders.ROLE, role);
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        return http.postForEntity("/api/v1/orders/" + orderId + "/cancel", new HttpEntity<>(headers), String.class);
    }

    private void assertError(ResponseEntity<String> response, int status, String code) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo(code);
    }

    private Order seed() {
        Order order = orders.saveAndFlush(Order.create(UUID.randomUUID(), ownerId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "오전 도착 희망"));
        fixtureIds.add(order.getId());
        return order;
    }
}
