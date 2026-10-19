package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 PATCH → 쓰기 트랜잭션 → PostgreSQL의 권한·상태·잠금·Audit를 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"
})
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "slack.webhook-url="
})
class OrderUpdateHttpIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDateTime BEFORE = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @MockitoBean
    private CompanyReader companyReader;
    @MockitoBean
    private DeliveryGateway deliveryGateway;
    @MockitoBean
    private OrderNotificationSender notificationSender;

    private final UUID ownerId = UUID.randomUUID();
    private final List<UUID> fixtureIds = new ArrayList<>();

    @BeforeEach
    void configureHttp() {
        // Note: HttpURLConnection 기반 기본 팩토리는 PATCH를 지원하지 않는다. Java 17 HTTP 클라이언트를 사용한다.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        http.getRestTemplate().setRequestFactory(factory);
    }

    @AfterEach
    void cleanUpAndCheckSideEffects() {
        try {
            verifyNoInteractions(companyReader, deliveryGateway, notificationSender);
        } finally {
            fixtureIds.forEach(id -> jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MASTER"})
    @DisplayName("주문자와 MASTER의 PATCH는 요청사항·수정 Audit만 바꾸고 커밋된 결과를 반환한다")
    void commitsNoteAndAuditOnly(String role) throws Exception {
        UUID id = seed();
        Order before = orderRepository.findById(id).orElseThrow();
        String body = mapper.writeValueAsString(Map.of("requestNote", "오후 도착 희망", "quantity", 1,
                "productId", UUID.randomUUID(), "unitPrice", 1, "totalPrice", 1, "status", "CANCELED"));

        JsonNode result = data(patch(id, body, role.equals("USER") ? ownerId : UUID.randomUUID(), role));
        Order after = orderRepository.findById(id).orElseThrow();

        assertThat(after.getRequestNote()).isEqualTo("오후 도착 희망");
        assertThat(after.getUpdatedBy()).isEqualTo("note-editor");
        assertThat(after.getUpdatedAt()).isAfter(BEFORE);
        assertThat(after).usingRecursiveComparison().ignoringFields("requestNote", "updatedAt", "updatedBy").isEqualTo(before);
        assertThat(result.path("requestNote").asText()).isEqualTo(after.getRequestNote());
        assertThat(result.path("totalPrice").asLong()).isEqualTo(600_000L);
        assertThat(LocalDateTime.parse(result.path("updatedAt").asText()))
                .isCloseTo(after.getUpdatedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("500자 요청사항은 HTTP와 PostgreSQL을 거쳐 그대로 저장한다")
    void acceptsMaximumLength() throws Exception {
        UUID id = seed();
        String note = "가".repeat(500);

        assertThat(data(patch(id, noteBody(note))).path("requestNote").asText()).isEqualTo(note);
        assertThat(orderRepository.findById(id).orElseThrow().getRequestNote()).isEqualTo(note);
    }

    @Test
    @DisplayName("같은 요청사항을 다시 보내면 성공하며 불필요하게 수정 시각을 바꾸지 않는다")
    void unchangedNoteKeepsAudit() throws Exception {
        UUID id = seed();

        JsonNode result = data(patch(id, noteBody("기존 요청사항")));

        assertThat(LocalDateTime.parse(result.path("updatedAt").asText())).isEqualTo(BEFORE);
        assertThat(orderRepository.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(BEFORE);
    }

    @Test
    @DisplayName("다른 USER의 수정은 403이며 요청사항과 Audit를 바꾸지 않는다")
    void deniesOtherOwner() throws Exception {
        UUID id = seed();
        Order before = orderRepository.findById(id).orElseThrow();

        assertError(patch(id, noteBody("변경 요청"), UUID.randomUUID(), "USER"), 403, "FORBIDDEN");

        assertThat(orderRepository.findById(id).orElseThrow()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    @DisplayName("없는 주문과 논리 삭제된 주문은 잠금 조회에서도 404다")
    void rejectsMissingAndDeleted() throws Exception {
        UUID id = seed();
        jdbc.update("UPDATE order_schema.p_orders SET deleted_at=?, deleted_by='fixture' WHERE order_id=?", BEFORE, id);

        assertError(patch(id, noteBody("변경 요청")), 404, "ORDER_NOT_FOUND");
        assertError(patch(UUID.randomUUID(), noteBody("변경 요청")), 404, "ORDER_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT request_note FROM order_schema.p_orders WHERE order_id=?", String.class, id))
                .isEqualTo("기존 요청사항");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MASTER"})
    @DisplayName("취소된 주문의 PATCH는 주문자와 MASTER 모두 409이며 데이터를 유지한다")
    void rejectsCanceledOrder(String role) throws Exception {
        UUID id = seed();
        jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED' WHERE order_id=?", id);
        Order before = orderRepository.findById(id).orElseThrow();

        assertError(patch(id, noteBody("변경 요청"), role.equals("USER") ? ownerId : UUID.randomUUID(), role),
                409, "INVALID_STATUS_TRANSITION");

        assertThat(orderRepository.findById(id).orElseThrow()).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "LONG"})
    @DisplayName("입력 검증 실패는 400이며 DB의 요청사항과 Audit를 유지한다")
    void invalidNoteDoesNotWrite(String note) throws Exception {
        UUID id = seed();
        Order before = orderRepository.findById(id).orElseThrow();

        assertError(patch(id, noteBody(note.equals("LONG") ? "가".repeat(501) : note)), 400, "VALIDATION_ERROR");

        assertThat(orderRepository.findById(id).orElseThrow()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    @DisplayName("flush 후에도 트랜잭션을 롤백하면 요청사항과 Audit가 함께 원복된다")
    void rollsBackNoteAndAudit() {
        UUID id = seed();
        Order before = orderRepository.findById(id).orElseThrow();

        transactions.executeWithoutResult(status -> {
            orderService.updateRequestNote(new UserContext(ownerId, "editor", Role.USER, false), id,
                    new UpdateOrderCommand("롤백할 요청사항"));
            assertThat(jdbc.queryForObject("SELECT request_note FROM order_schema.p_orders WHERE order_id=?", String.class, id))
                    .isEqualTo("롤백할 요청사항");
            status.setRollbackOnly();
        });

        assertThat(orderRepository.findById(id).orElseThrow()).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "delete"})
    @DisplayName("동시에 취소·삭제 중이면 잠금 해제 후 최신 상태로 거부하여 요청사항을 덮어쓰지 않는다")
    void waitsForConcurrentStateChange(String change) throws Exception {
        UUID id = seed();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> pending = transactions.execute(status -> {
                orderRepository.findByIdForUpdate(id).orElseThrow();
                int blockerPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                if (change.equals("cancel")) {
                    jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED' WHERE order_id=?", id);
                } else {
                    jdbc.update("UPDATE order_schema.p_orders SET deleted_at=?, deleted_by='fixture' WHERE order_id=?", BEFORE, id);
                }
                Future<ResponseEntity<String>> request = executor.submit(() -> patch(id, noteBody("경쟁 요청")));
                // Note: 시간 지연을 추측하지 않고 PostgreSQL이 실제 잠금 대기를 보고한 뒤 첫 트랜잭션을 커밋한다.
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                                Long.class, blockerPid)).isGreaterThan(0));
                return request;
            });

            assertThat(pending).isNotNull();
            assertError(pending.get(10, TimeUnit.SECONDS), change.equals("cancel") ? 409 : 404,
                    change.equals("cancel") ? "INVALID_STATUS_TRANSITION" : "ORDER_NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT request_note FROM order_schema.p_orders WHERE order_id=?", String.class, id))
                    .isEqualTo("기존 요청사항");
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("OpenAPI는 PATCH와 필수 요청사항·최대 길이 500을 명시한다")
    void documentsUpdateContract() throws Exception {
        JsonNode document = mapper.readTree(http.getForEntity("/v3/api-docs", String.class).getBody());

        assertThat(document.path("paths").path("/api/v1/orders/{orderId}").path("patch").path("summary").asText())
                .isEqualTo("O-04 요청사항 수정");
        JsonNode schema = document.path("components").path("schemas").path("UpdateOrderRequest");
        assertThat(schema.path("required").toString()).contains("requestNote");
        assertThat(schema.path("properties").path("requestNote").path("maxLength").asInt()).isEqualTo(500);
    }

    private UUID seed() {
        Order saved = orderRepository.saveAndFlush(Order.create(UUID.randomUUID(), ownerId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "기존 요청사항"));
        fixtureIds.add(saved.getId());
        jdbc.update("UPDATE order_schema.p_orders SET created_at=?, updated_at=? WHERE order_id=?", BEFORE, BEFORE, saved.getId());
        return saved.getId();
    }

    private String noteBody(String note) throws Exception {
        return mapper.writeValueAsString(Map.of("requestNote", note));
    }

    private ResponseEntity<String> patch(UUID id, String body) {
        return patch(id, body, ownerId, "USER");
    }

    private ResponseEntity<String> patch(UUID id, String body, UUID caller, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(UserHeaders.USER_ID, caller.toString());
        headers.set(UserHeaders.USERNAME, "note-editor");
        headers.set(UserHeaders.ROLE, role);
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        return http.exchange("/api/v1/orders/" + id, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return mapper.readTree(response.getBody()).path("data");
    }

    private void assertError(ResponseEntity<String> response, int status, String code) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo(code);
    }
}
