package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.OrderWriter;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationAttempt;
import com.sparta.logistics.order.support.DirectCreationExecutorConfig;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.RecordedRequest;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** 실제 Tomcat → Controller → application → Feign HTTP → PostgreSQL을 연결한다. 외부 서버 응답만 테스트 대역이다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(DirectCreationExecutorConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.default_schema=order_schema",
        "slack.webhook-url=",
        "spring.cloud.openfeign.client.config.default.connectTimeout=1000",
        "spring.cloud.openfeign.client.config.default.readTimeout=200"
})
class OrderHttpIntegrationTest extends PostgresIntegrationTest {

    private static final HttpStubServer STUB = new HttpStubServer();

    @DynamicPropertySource
    static void configureClients(DynamicPropertyRegistry registry) {
        for (String client : List.of("order-company", "order-delivery-create", "order-delivery-delete")) {
            registry.add("spring.cloud.openfeign.client.config." + client + ".url", STUB::url);
        }
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrderWriter writer;

    private final UUID userId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID departureHubId = UUID.randomUUID();
    private final UUID destinationHubId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final Set<UUID> directFixtureIds = new HashSet<>();

    @BeforeEach
    void resetStub() {
        // Note: 테스트 드라이버의 Apache HTTP 클라이언트가 503을 재전송하면 주문 자체가 두 번 실행된다.
        // 드라이버의 상태 코드 재시도를 제거하고 Feign·보상의 재시도만 측정한다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(10_000);
        http.getRestTemplate().setRequestFactory(factory);
        STUB.reset(this::normalReply);
    }

    @AfterEach
    void cleanUpCreatedFixtures() throws Exception {
        jdbc.execute("ALTER TABLE order_schema.p_order_creations DROP CONSTRAINT IF EXISTS ck_test_creation_completion");
        Set<UUID> ids = new HashSet<>(directFixtureIds);
        for (RecordedRequest request : STUB.requests("POST", "/internal/deliveries")) {
            ids.add(UUID.fromString(mapper.readTree(request.body()).path("orderId").asText()));
        }
        for (UUID id : ids) {
            jdbc.update("DELETE FROM order_schema.p_order_creations WHERE order_id=?", id);
            jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id);
        }
    }

    @Test
    @DisplayName("실제 HTTP 생성 요청이 Feign 계약·사용자 헤더·배송 정보·주문 DB 저장까지 연결된다")
    void createsThroughHttpAndFeign() throws Exception {
        JsonNode progress = acceptAndGetProgress();

        assertThat(progress.path("status").asText()).isEqualTo("COMPLETED");
        JsonNode result = progress.path("order");
        UUID orderId = UUID.fromString(result.path("orderId").asText());
        assertThat(result.path("ordererId").asText()).isEqualTo(userId.toString());
        assertThat(result.path("unitPrice").asLong()).isEqualTo(12_000L);
        assertThat(result.path("totalPrice").asLong()).isEqualTo(600_000L);
        assertThat(result.path("status").asText()).isEqualTo("ORDERED");
        assertThat(result.path("deliveryId").asText()).isEqualTo(deliveryId.toString());
        assertThat(jdbc.queryForObject("SELECT created_by FROM order_schema.p_orders WHERE order_id = ?",
                String.class, orderId)).isEqualTo("junhyun");

        List<RecordedRequest> creates = STUB.requests("POST", "/internal/deliveries");
        assertThat(creates).hasSize(1);
        assertThat(mapper.readTree(creates.get(0).body())).isEqualTo(mapper.valueToTree(Map.of(
                "orderId", orderId, "supplierCompanyId", supplierId, "receiverCompanyId", receiverId,
                "departureHubId", departureHubId, "destinationHubId", destinationHubId,
                "pickupAddress", "서울 집하지", "deliveryAddress", "부산 배송지",
                "recipientName", "수령업체", "receiverManagerId", userId)));
        assertThat(STUB.requests("GET", "/internal/companies/")).hasSize(2);
        assertThat(STUB.requests("GET", "/internal/products/")).hasSize(1);
        for (RecordedRequest request : STUB.requests("GET", "/internal/")) {
            assertThat(request.header(UserHeaders.USER_ID)).isEqualTo(userId.toString());
            assertThat(request.header(UserHeaders.USERNAME)).isEqualTo("junhyun");
            assertThat(request.header(UserHeaders.ROLE)).isEqualTo("USER");
        }
        assertThat(creates.get(0).header(UserHeaders.USER_ID)).isEqualTo(userId.toString());
        assertCreationRecord(orderId, "COMPLETED", null);
        assertThat(jdbc.queryForObject("SELECT orderer_id FROM order_schema.p_order_creations WHERE order_id=?", UUID.class, orderId))
                .isEqualTo(userId);
        assertThat(jdbc.queryForObject("SELECT created_by FROM order_schema.p_order_creations WHERE order_id=?", String.class, orderId))
                .isEqualTo("junhyun");
    }

    @Test
    @DisplayName("Feign으로 조회한 수령업체의 관리자가 아니면 403이고 배송을 만들지 않는다")
    void rejectsNonOwnerFromActualCompanyResponse() throws Exception {
        STUB.reset(request -> request.path().equals("/internal/companies/" + receiverId)
                ? company(receiverId, "수령업체", destinationHubId, "부산 배송지", UUID.randomUUID()) : normalReply(request));

        assertError(create(), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(STUB.requests("POST", "/internal/deliveries")).isEmpty();
    }

    @Test
    @DisplayName("내부 API의 404 에러 코드를 공통 Feign 디코더가 외부 응답까지 보존한다")
    void preservesDownstreamNotFound() throws Exception {
        STUB.reset(request -> request.path().equals("/internal/products/" + productId)
                ? failure(404, "PRODUCT_NOT_FOUND") : normalReply(request));

        assertError(create(), HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND");
        assertThat(STUB.requests("POST", "/internal/deliveries")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-price", "fractional-price", "wrapped"})
    @DisplayName("가격 누락·소수 가격·감싼 응답을 정상 상품으로 취급하지 않는다")
    void rejectsMalformedProductResponse(String scenario) throws Exception {
        String productJson = switch (scenario) {
            case "missing-price" -> json(Map.of("productId", productId, "companyId", supplierId, "name", "상품"));
            case "fractional-price" -> json(Map.of("productId", productId, "companyId", supplierId, "name", "상품", "price", 12.5));
            default -> json(Map.of("status", 200, "data", Map.of("productId", productId, "companyId", supplierId,
                    "name", "상품", "price", 12_000)));
        };
        STUB.reset(request -> request.path().equals("/internal/products/" + productId)
                ? Reply.json(200, productJson) : normalReply(request));

        assertError(create(), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        assertThat(STUB.requests("POST", "/internal/deliveries")).isEmpty();
    }

    @Test
    @DisplayName("배송담당자 없음은 404로 전달하고 배송 삭제를 호출하지 않는다")
    void preservesDeliveryRejectionWithoutCompensation() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? failure(404, "DELIVERY_MANAGER_NOT_FOUND") : normalReply(request));

        assertFailedProgress(acceptAndGetProgress(), "ABORTED", "DELIVERY_MANAGER_NOT_FOUND");
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/")).isEmpty();
        assertCreationRecord(createdOrderId(), "ABORTED", null);
    }

    @Test
    @DisplayName("배송 5xx는 재시도 없이 503으로 바뀌고 생성에 쓴 orderId로 삭제한다")
    void compensatesDeliveryServerError() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? failure(500, "INTERNAL_SERVER_ERROR") : normalReply(request));

        assertFailedProgress(acceptAndGetProgress(), "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
        assertThat(STUB.requests("POST", "/internal/deliveries")).hasSize(1);
        assertCompensated(1);
    }

    @Test
    @DisplayName("배송 생성 타임아웃은 같은 주문 ID로 3회 시도한 뒤 삭제 보상한다")
    void retriesCreateWithSameOrderId() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? normalReply(request).delayed(800) : normalReply(request));

        assertFailedProgress(acceptAndGetProgress(), "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
        List<RecordedRequest> creates = STUB.requests("POST", "/internal/deliveries");
        assertThat(creates).hasSize(3);
        assertThat(creates).extracting(RecordedRequest::body).containsOnly(creates.get(0).body());
        assertCompensated(1);
    }

    @Test
    @DisplayName("배송 생성 첫 타임아웃 뒤 재시도가 성공하면 같은 주문 ID로 한 주문만 저장한다")
    void retryCanRecover() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        STUB.reset(request -> request.method().equals("POST") && attempts.incrementAndGet() == 1
                ? normalReply(request).delayed(800) : normalReply(request));

        assertThat(acceptAndGetProgress().path("status").asText()).isEqualTo("COMPLETED");
        List<RecordedRequest> creates = STUB.requests("POST", "/internal/deliveries");
        assertThat(creates).hasSize(2);
        assertThat(creates).extracting(RecordedRequest::body).containsOnly(creates.get(0).body());
        UUID orderId = UUID.fromString(mapper.readTree(creates.get(0).body()).path("orderId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/")).isEmpty();
        assertCreationRecord(orderId, "COMPLETED", null);
    }

    @Test
    @DisplayName("삭제 타임아웃의 실제 HTTP 호출은 서비스 재시도 3회이며 Feign이 중복 재시도하지 않는다")
    void deletionDoesNotMultiplyRetries() throws Exception {
        STUB.reset(request -> {
            if (request.method().equals("POST")) {
                return failure(500, "INTERNAL_SERVER_ERROR");
            }
            return request.method().equals("DELETE") ? normalReply(request).delayed(800) : normalReply(request);
        });

        assertFailedProgress(acceptAndGetProgress(), "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
        assertCompensated(3);
    }

    @Test
    @DisplayName("배송 생성 응답의 ID가 빠져도 같은 orderId로 보상한다")
    void compensatesMalformedDeliveryResponse() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? Reply.json(201, "{}") : normalReply(request));

        assertFailedProgress(acceptAndGetProgress(), "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
        assertCompensated(1);
    }

    @Test
    @DisplayName("업체 GET 타임아웃은 3회 시도하고 배송 변경 없이 503으로 끝난다")
    void retriesReadOnlyLookup() throws Exception {
        STUB.reset(request -> request.path().equals("/internal/companies/" + supplierId)
                ? normalReply(request).delayed(800) : normalReply(request));

        assertError(create(), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        assertThat(STUB.requests("GET", "/internal/companies/" + supplierId)).hasSize(3);
        assertThat(STUB.requests("POST", "/internal/deliveries")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_order_creations WHERE orderer_id=?", Integer.class, userId)).isZero();
    }

    @Test
    @DisplayName("완료 기록 저장이 실패하면 실제 HTTP에서도 주문을 롤백하고 배송 삭제 후 ABORTED로 남긴다")
    void rollsBackWhenCompletionRecordFails() throws Exception {
        failCompletionRecord();

        assertFailedProgress(acceptAndGetProgress(), "ABORTED", "INTERNAL_SERVER_ERROR");

        UUID orderId = createdOrderId();
        assertCreationRecord(orderId, "ABORTED", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id=?", Integer.class, orderId)).isZero();
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/" + orderId)).hasSize(1);
    }

    @Test
    @DisplayName("주문 미저장 확인 뒤 배송 삭제도 실패하면 실제 배송 ID와 삭제 실패 사유를 보존한다")
    void persistsFailedCleanup() throws Exception {
        failCompletionRecord();
        STUB.reset(request -> request.method().equals("DELETE") ? failure(500, "INTERNAL_SERVER_ERROR") : normalReply(request));

        assertFailedProgress(acceptAndGetProgress(), "RECOVERY_REQUIRED", "DELIVERY_DELETE_FAILED");

        UUID orderId = createdOrderId();
        assertCreationRecord(orderId, "RECOVERY_REQUIRED", "DELIVERY_DELETE_FAILED");
        assertThat(jdbc.queryForObject("SELECT delivery_id FROM order_schema.p_order_creations WHERE order_id=?", UUID.class, orderId))
                .isEqualTo(deliveryId);
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/" + orderId)).hasSize(3);
    }

    @Test
    @DisplayName("타임아웃 보상 뒤 지연된 배송 생성이 끝나도 복구 기록을 자동 종료하지 않는다")
    void retainsRecoveryAfterLateDeliveryCreation() throws Exception {
        CountDownLatch finishCreation = new CountDownLatch(1);
        AtomicInteger finished = new AtomicInteger();
        STUB.reset(request -> {
            if (request.method().equals("POST")) {
                try { assertThat(finishCreation.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                finished.incrementAndGet();
            }
            return normalReply(request);
        });
        try {
            assertFailedProgress(acceptAndGetProgress(), "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
            assertCompensated(1);
            assertThat(finished.get()).isZero();
            finishCreation.countDown();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(finished.get()).isEqualTo(3));

            assertCreationRecord(createdOrderId(), "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
        } finally {
            finishCreation.countDown();
        }
    }

    private void failCompletionRecord() {
        jdbc.execute("ALTER TABLE order_schema.p_order_creations ADD CONSTRAINT ck_test_creation_completion "
                + "CHECK (orderer_id <> '" + userId + "'::uuid OR status <> 'COMPLETED')");
    }

    private UUID createdOrderId() throws Exception {
        return UUID.fromString(mapper.readTree(STUB.requests("POST", "/internal/deliveries").get(0).body()).path("orderId").asText());
    }

    private void assertCreationRecord(UUID orderId, String status, String reason) {
        assertThat(jdbc.queryForObject("SELECT status FROM order_schema.p_order_creations WHERE order_id=?", String.class, orderId)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM order_schema.p_order_creations WHERE order_id=?", String.class, orderId)).isEqualTo(reason);
    }

    @Test
    @DisplayName("같은 키는 회사 장애·주문 변경 후에도 최초 201 본문을 재사용하며 외부 호출을 늘리지 않는다")
    void replaysOriginalResponseThroughHttp() throws Exception {
        AtomicBoolean failReads = new AtomicBoolean();
        STUB.reset(request -> failReads.get() && request.method().equals("GET") ? failure(503, "SERVICE_UNAVAILABLE") : normalReply(request));
        UUID id = accept("replay-key", defaultBody());
        JsonNode first = progress(id).path("order");
        assertThat(first.path("orderId").asText()).isEqualTo(id.toString());
        jdbc.update("UPDATE order_schema.p_orders SET request_note='changed', status='CANCELED', updated_at=now() WHERE order_id=?", id);
        failReads.set(true);

        ResponseEntity<String> replay = create("replay-key", defaultBody());

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(mapper.readTree(replay.getBody()).path("data")).isEqualTo(first);
        assertThat(STUB.requests("POST", "/internal/deliveries")).hasSize(1);
        assertThat(STUB.requests("GET", "/internal/")).hasSize(3);
    }

    @Test
    @DisplayName("JSON 순서·공백·UUID 대소문자·요청사항 생략과 null은 같은 논리 요청으로 인식한다")
    void comparesParsedRequestRatherThanRawJson() throws Exception {
        ObjectNode firstBody = (ObjectNode) mapper.readTree(defaultBody());
        firstBody.remove("requestNote");
        UUID id = accept("semantic-key", firstBody.toString());
        JsonNode first = progress(id).path("order");
        String secondBody = """
                { "requestNote": null, "quantity": 50, "productId": "%s",
                  "receiverCompanyId": "%s", "supplierCompanyId": "%s" }
                """.formatted(productId.toString().toUpperCase(), receiverId, supplierId);

        ResponseEntity<String> replay = create("semantic-key", secondBody);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(mapper.readTree(replay.getBody()).path("data")).isEqualTo(first);
        assertThat(STUB.requests("POST", "/internal/deliveries")).hasSize(1);
    }

    @Test
    @DisplayName("같은 키로 수량을 바꾸면 409이고 회사·배송을 다시 호출하지 않는다")
    void rejectsChangedBodyThroughHttp() throws Exception {
        accept("conflict-key", defaultBody());
        ObjectNode changed = (ObjectNode) mapper.readTree(defaultBody());
        changed.put("quantity", 51);

        assertError(create("conflict-key", changed.toString()), HttpStatus.CONFLICT, "ORDER_IDEMPOTENCY_KEY_CONFLICT");
        assertThat(STUB.requests("POST", "/internal/deliveries")).hasSize(1);
        assertThat(STUB.requests("GET", "/internal/")).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("실패 종료와 복구 대기의 같은 키는 원래 요청을 재실행하지 않고 409로 구분한다")
    void refusesRetryOfFailedAttempt(boolean uncertain) throws Exception {
        STUB.reset(request -> request.method().equals("POST")
                ? failure(uncertain ? 500 : 404, uncertain ? "INTERNAL_SERVER_ERROR" : "DELIVERY_MANAGER_NOT_FOUND") : normalReply(request));
        UUID orderId = accept("failed-key", defaultBody());
        assertFailedProgress(progress(orderId), uncertain ? "RECOVERY_REQUIRED" : "ABORTED",
                uncertain ? "DELIVERY_RESULT_UNKNOWN" : "DELIVERY_MANAGER_NOT_FOUND");

        assertError(create("failed-key", defaultBody()), HttpStatus.CONFLICT,
                uncertain ? "ORDER_CREATION_RECOVERY_REQUIRED" : "ORDER_CREATION_ABORTED");
        assertThat(STUB.requests("POST", "/internal/deliveries")).hasSize(1);
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/")).hasSize(uncertain ? 1 : 0);
    }

    @Test
    @DisplayName("처리 중인 키의 HTTP 재요청은 외부 호출 없이 202와 같은 orderId를 돌려준다")
    void returnsAcceptedForInProgressKey() throws Exception {
        OrderCreationAttempt attempt = new OrderCreationAttempt(UUID.randomUUID(), UUID.randomUUID(), userId);
        directFixtureIds.add(attempt.orderId());
        writer.start(attempt, new CreateOrderCommand(supplierId, receiverId, productId, 50, "오전 도착 희망").identity("pending-key"));

        ResponseEntity<String> response = create("pending-key", defaultBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode data = mapper.readTree(response.getBody()).path("data");
        assertThat(data.path("orderId").asText()).isEqualTo(attempt.orderId().toString());
        assertThat(data.path("status").asText()).isEqualTo("PROCESSING");
        assertThat(STUB.requests("GET", "/")).isEmpty();
        assertThat(STUB.requests("POST", "/")).isEmpty();
    }

    @Test
    @DisplayName("접수 직후 진행 조회는 COMPLETED이고 order가 재요청의 최초 응답과 같다")
    void progressShowsCompletedOrderMatchingReplay() throws Exception {
        ResponseEntity<String> accepted = create("progress-key", defaultBody());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode acceptedData = mapper.readTree(accepted.getBody()).path("data");
        assertThat(acceptedData.path("status").asText()).isEqualTo("PROCESSING");
        UUID orderId = UUID.fromString(acceptedData.path("orderId").asText());

        JsonNode progress = progress(orderId);

        assertThat(progress.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(progress.path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(progress.path("failureCode").isNull()).isTrue();
        assertThat(progress.path("order").path("orderId").asText()).isEqualTo(orderId.toString());
        ResponseEntity<String> replay = create("progress-key", defaultBody());
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(mapper.readTree(replay.getBody()).path("data")).isEqualTo(progress.path("order"));
    }

    @Test
    @DisplayName("OpenAPI에 필수 멱등키 헤더와 길이·문자 규칙이 표시된다")
    void documentsIdempotencyKey() throws Exception {
        JsonNode doc = mapper.readTree(http.getForEntity("/v3/api-docs", String.class).getBody());
        JsonNode parameters = doc.path("paths").path("/api/v1/orders").path("post").path("parameters");
        JsonNode key = java.util.stream.StreamSupport.stream(parameters.spliterator(), false)
                .filter(parameter -> parameter.path("name").asText().equals("Idempotency-Key")).findFirst().orElseThrow();
        assertThat(key.path("in").asText()).isEqualTo("header");
        assertThat(key.path("required").asBoolean()).isTrue();
        assertThat(key.path("schema").path("maxLength").asInt()).isEqualTo(128);
        assertThat(key.path("schema").path("minLength").asInt()).isEqualTo(1);
        assertThat(key.path("schema").path("pattern").asText()).isEqualTo("[A-Za-z0-9._:-]+");
    }

    /** 접수(202) 뒤 주문 ID를 꺼낸다. 접수 전 단계 실패는 이 단언에서 드러난다. */
    private UUID accept(String key, String body) throws Exception {
        ResponseEntity<String> response = create(key, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode data = mapper.readTree(response.getBody()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("PROCESSING");
        return UUID.fromString(data.path("orderId").asText());
    }

    /** 새 키로 접수하고 진행 조회 결과(data)를 돌려준다. 테스트 실행기가 요청 스레드에서 처리하므로 접수 시점에 처리가 끝나 있다. */
    private JsonNode acceptAndGetProgress() throws Exception {
        return progress(accept(UUID.randomUUID().toString(), defaultBody()));
    }

    private JsonNode progress(UUID orderId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(UserHeaders.USER_ID, userId.toString());
        headers.set(UserHeaders.USERNAME, "junhyun");
        headers.set(UserHeaders.ROLE, "USER");
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        ResponseEntity<String> response = http.exchange("/api/v1/orders/creations/" + orderId, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).path("data");
    }

    private void assertFailedProgress(JsonNode progress, String status, String failureCode) {
        assertThat(progress.path("status").asText()).isEqualTo(status);
        assertThat(progress.path("failureCode").asText()).isEqualTo(failureCode);
        assertThat(progress.path("failureMessage").asText()).isNotBlank();
        assertThat(progress.path("order").isNull()).isTrue();
    }

    private ResponseEntity<String> create() {
        return create(UUID.randomUUID().toString(), defaultBody());
    }

    private ResponseEntity<String> create(String key, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(UserHeaders.USER_ID, userId.toString());
        headers.set(UserHeaders.USERNAME, "junhyun");
        headers.set(UserHeaders.ROLE, "USER");
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), String.class);
    }

    private String defaultBody() {
        return json(Map.of("supplierCompanyId", supplierId, "receiverCompanyId", receiverId,
                "productId", productId, "quantity", 50, "requestNote", "오전 도착 희망"));
    }

    private Reply normalReply(RecordedRequest request) {
        if (request.path().equals("/internal/companies/" + supplierId)) {
            return company(supplierId, "공급업체", departureHubId, "서울 집하지", UUID.randomUUID());
        }
        if (request.path().equals("/internal/companies/" + receiverId)) {
            return company(receiverId, "수령업체", destinationHubId, "부산 배송지", userId);
        }
        if (request.path().equals("/internal/products/" + productId)) {
            return Reply.json(200, json(Map.of("productId", productId, "companyId", supplierId, "name", "마른오징어", "price", 12_000)));
        }
        if (request.method().equals("POST") && request.path().equals("/internal/deliveries")) {
            return Reply.json(201, json(Map.of("deliveryId", deliveryId, "status", "PICKUP_WAITING")));
        }
        if (request.method().equals("DELETE") && request.path().startsWith("/internal/deliveries/orders/")) {
            return Reply.json(204, "");
        }
        return failure(404, "STUB_ROUTE_NOT_FOUND");
    }

    private Reply company(UUID id, String name, UUID hubId, String address, UUID managerId) {
        return Reply.json(200, json(Map.of("companyId", id, "name", name, "hubId", hubId, "address", address, "managerId", managerId)));
    }

    private Reply failure(int status, String code) {
        return Reply.json(status, json(Map.of("status", status, "code", code, "message", "테스트 오류")));
    }

    private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo(code);
    }

    private void assertCompensated(int attempts) throws Exception {
        UUID orderId = UUID.fromString(mapper.readTree(STUB.requests("POST", "/internal/deliveries").get(0).body())
                .path("orderId").asText());
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/"))
                .hasSize(attempts).allSatisfy(request -> {
                    assertThat(request.path()).isEqualTo("/internal/deliveries/orders/" + orderId);
                    assertThat(request.header(UserHeaders.USER_ID)).isEqualTo(userId.toString());
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?",
                Integer.class, orderId)).isZero();
        assertCreationRecord(orderId, "RECOVERY_REQUIRED", "DELIVERY_RESULT_UNKNOWN");
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
