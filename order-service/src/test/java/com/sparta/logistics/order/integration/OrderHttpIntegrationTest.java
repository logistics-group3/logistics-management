package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.RecordedRequest;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
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
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema",
        "spring.jpa.properties.hibernate.default_schema=order_schema",
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

    private final UUID userId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID departureHubId = UUID.randomUUID();
    private final UUID destinationHubId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

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

    @Test
    @DisplayName("실제 HTTP 생성 요청이 Feign 계약·사용자 헤더·배송 정보·주문 DB 저장까지 연결된다")
    void createsThroughHttpAndFeign() throws Exception {
        ResponseEntity<String> response = create();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode result = mapper.readTree(response.getBody()).path("data");
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

        assertError(create(), HttpStatus.NOT_FOUND, "DELIVERY_MANAGER_NOT_FOUND");
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/")).isEmpty();
    }

    @Test
    @DisplayName("배송 5xx는 재시도 없이 503으로 바뀌고 생성에 쓴 orderId로 삭제한다")
    void compensatesDeliveryServerError() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? failure(500, "INTERNAL_SERVER_ERROR") : normalReply(request));

        assertError(create(), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        assertThat(STUB.requests("POST", "/internal/deliveries")).hasSize(1);
        assertCompensated(1);
    }

    @Test
    @DisplayName("배송 생성 타임아웃은 같은 주문 ID로 3회 시도한 뒤 삭제 보상한다")
    void retriesCreateWithSameOrderId() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? normalReply(request).delayed(800) : normalReply(request));

        assertError(create(), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
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

        assertThat(create().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<RecordedRequest> creates = STUB.requests("POST", "/internal/deliveries");
        assertThat(creates).hasSize(2);
        assertThat(creates).extracting(RecordedRequest::body).containsOnly(creates.get(0).body());
        UUID orderId = UUID.fromString(mapper.readTree(creates.get(0).body()).path("orderId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(STUB.requests("DELETE", "/internal/deliveries/orders/")).isEmpty();
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

        assertError(create(), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        assertCompensated(3);
    }

    @Test
    @DisplayName("배송 생성 응답의 ID가 빠져도 같은 orderId로 보상한다")
    void compensatesMalformedDeliveryResponse() throws Exception {
        STUB.reset(request -> request.method().equals("POST") ? Reply.json(201, "{}") : normalReply(request));

        assertError(create(), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
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
    }

    private ResponseEntity<String> create() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(UserHeaders.USER_ID, userId.toString());
        headers.set(UserHeaders.USERNAME, "junhyun");
        headers.set(UserHeaders.ROLE, "USER");
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        String body = json(Map.of("supplierCompanyId", supplierId, "receiverCompanyId", receiverId,
                "productId", productId, "quantity", 50, "requestNote", "오전 도착 희망"));
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), String.class);
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
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
