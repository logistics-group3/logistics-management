package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 실제 HTTP → 권한 범위 → QueryDSL → PostgreSQL의 검색·count·논리 삭제를 함께 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"
})
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "slack.webhook-url="
})
class OrderQueryHttpIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private CompanyReader companyReader;
    @MockitoBean
    private DeliveryGateway deliveryGateway;
    @MockitoBean
    private OrderNotificationSender notificationSender;

    private final UUID userId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final List<UUID> fixtureOrderIds = new ArrayList<>();

    @BeforeEach
    void configureHttp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(10_000);
        http.getRestTemplate().setRequestFactory(factory);
    }

    @AfterEach
    void cleanUpAndCheckSideEffects() {
        try {
            verifyNoInteractions(companyReader, deliveryGateway, notificationSender);
        } finally {
            // Note: 공유 Testcontainers DB에서 이 테스트가 넣은 행만 정리해 다른 테스트의 임시 제약에 영향을 주지 않는다.
            fixtureOrderIds.forEach(id -> jdbc.update("DELETE FROM order_schema.p_orders WHERE order_id=?", id));
        }
    }

    @Test
    @DisplayName("OpenAPI의 검색 파라미터와 기본 페이지 크기가 실제 HTTP 계약과 일치한다")
    void documentsSearchContract() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode parameters = mapper.readTree(response.getBody()).path("paths").path("/api/v1/orders").path("get").path("parameters");
        List<String> names = StreamSupport.stream(parameters.spliterator(), false)
                .map(parameter -> parameter.path("name").asText()).toList();
        assertThat(names).containsExactlyInAnyOrder("status", "supplierCompanyId", "receiverCompanyId", "productId",
                "startDate", "endDate", "page", "size", "sort");
        JsonNode size = StreamSupport.stream(parameters.spliterator(), false)
                .filter(parameter -> parameter.path("name").asText().equals("size")).findFirst().orElseThrow();
        assertThat(size.path("schema").path("default").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("다른 주문자 파라미터를 넣어도 USER의 내용·전체 건수·다음 페이지는 본인 주문만 포함한다")
    void scopesContentAndCountToAuthenticatedOwner() throws Exception {
        for (int quantity = 1; quantity <= 12; quantity++) {
            seed(userId, quantity, CREATED_AT);
        }
        UUID otherUser = UUID.randomUUID();
        seed(otherUser, 1, CREATED_AT);
        seed(otherUser, 2, CREATED_AT);
        UUID deleted = seed(userId, 13, CREATED_AT);
        softDelete(deleted);

        JsonNode first = data(get("?sort=quantity,ASC&ordererId=" + otherUser));
        JsonNode second = data(get("?page=1&sort=quantity,ASC&ordererId=" + otherUser));

        assertThat(first.path("content")).hasSize(10);
        assertThat(first.path("totalElements").asLong()).isEqualTo(12);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(first.path("size").asInt()).isEqualTo(10);
        assertThat(second.path("content")).hasSize(2);
        assertThat(second.path("totalElements").asLong()).isEqualTo(12);
        assertThat(second.path("content").get(0).path("quantity").asInt()).isEqualTo(11);
        for (JsonNode row : first.path("content")) {
            assertThat(row.path("ordererId").asText()).isEqualTo(userId.toString());
        }
        assertThat(ids(first)).doesNotContain(deleted.toString());
    }

    @Test
    @DisplayName("MASTER는 검색 조건에 맞는 여러 주문자의 주문을 조회하며 삭제된 주문은 제외한다")
    void masterSearchesAcrossOwners() throws Exception {
        UUID own = seed(userId, 1, CREATED_AT);
        UUID others = seed(UUID.randomUUID(), 2, CREATED_AT.plusHours(1));
        UUID deleted = seed(UUID.randomUUID(), 3, CREATED_AT);
        softDelete(deleted);

        JsonNode page = data(get("?supplierCompanyId=" + supplierId, UUID.randomUUID(), "MASTER"));

        assertThat(ids(page)).containsExactly(others.toString(), own.toString());
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("sort").asText()).isEqualTo("createdAt,DESC");
    }

    @Test
    @DisplayName("상태·공급업체·수령업체·상품·기간 조건을 모두 AND로 적용한다")
    void combinesEveryFilter() throws Exception {
        UUID match = seed(userId, 1, CREATED_AT);
        jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED' WHERE order_id=?", match);
        seed(userId, 2, CREATED_AT); // 상태만 다르다.
        for (String column : List.of("supplier_company_id", "receiver_company_id", "product_id")) {
            UUID id = seed(userId, 3, CREATED_AT);
            jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED', " + column + "=? WHERE order_id=?",
                    UUID.randomUUID(), id);
        }
        for (LocalDateTime date : List.of(CREATED_AT.minusDays(1), CREATED_AT.plusDays(1))) {
            UUID id = seed(userId, 4, date);
            jdbc.update("UPDATE order_schema.p_orders SET status='CANCELED' WHERE order_id=?", id);
        }

        JsonNode page = data(get("?status=CANCELED&supplierCompanyId=" + supplierId + "&receiverCompanyId=" + receiverId
                + "&productId=" + productId + "&startDate=2026-10-15&endDate=2026-10-15"));

        assertThat(ids(page)).containsExactly(match.toString());
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("status").asText()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("기간 검색은 시작일 0시와 종료일 마지막 마이크로초를 포함하고 다음 날 0시는 제외한다")
    void includesWholeEndDate() throws Exception {
        LocalDateTime start = CREATED_AT.toLocalDate().atStartOfDay();
        seed(userId, 1, start.minusNanos(1000));
        UUID first = seed(userId, 2, start);
        UUID last = seed(userId, 3, start.plusDays(1).minusNanos(1000));
        seed(userId, 4, start.plusDays(1));

        JsonNode page = data(get("?startDate=2026-10-15&endDate=2026-10-15"));

        assertThat(ids(page)).containsExactly(last.toString(), first.toString());
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"startDate", "endDate"})
    @DisplayName("시작일 또는 종료일만 지정해도 해당 방향의 기간 조건을 적용한다")
    void supportsOneSidedDateRange(String parameter) throws Exception {
        UUID before = seed(userId, 1, CREATED_AT.minusDays(1));
        UUID middle = seed(userId, 2, CREATED_AT);
        UUID after = seed(userId, 3, CREATED_AT.plusDays(1));

        JsonNode page = data(get("?" + parameter + "=2026-10-15"));

        assertThat(ids(page)).containsExactlyElementsOf(parameter.equals("startDate")
                ? List.of(after.toString(), middle.toString()) : List.of(middle.toString(), before.toString()));
    }

    @Test
    @DisplayName("정렬 값이 모두 같아도 ID 보조 정렬로 페이지의 중복과 누락을 방지한다")
    void givesTiesAStableOrder() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int index = 0; index < 22; index++) {
            expected.add(seed(userId, 50, CREATED_AT).toString());
        }
        expected.sort(Comparator.naturalOrder());
        List<String> actual = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            JsonNode page = data(get("?sort=quantity,ASC&page=" + pageNumber));
            assertThat(page.path("totalElements").asLong()).isEqualTo(22);
            actual.addAll(ids(page));
        }

        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("허용된 복수 정렬은 요청한 필드 순서와 방향으로 적용한다")
    void supportsMultipleSortFields() throws Exception {
        UUID low = seed(userId, 1, CREATED_AT);
        UUID highLate = seed(userId, 2, CREATED_AT.plusHours(1));
        UUID highEarly = seed(userId, 2, CREATED_AT.minusHours(1));

        JsonNode page = data(get("?sort=quantity,DESC&sort=createdAt,ASC"));

        assertThat(ids(page)).containsExactly(highEarly.toString(), highLate.toString(), low.toString());
        assertThat(page.path("sort").asText()).isEqualTo("quantity,DESC;createdAt,ASC");
    }

    @ParameterizedTest
    @CsvSource({"10,10", "30,30", "50,50", "20,10"})
    @DisplayName("공통 페이지 크기 10·30·50을 허용하고 그 외 값은 10으로 보정한다")
    void usesCommonPageSizeRules(int requested, int expected) throws Exception {
        JsonNode page = data(get("?size=" + requested));

        assertThat(page.path("content")).isEmpty();
        assertThat(page.path("size").asInt()).isEqualTo(expected);
        assertThat(page.path("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("마지막 페이지 다음을 조회하면 빈 목록과 기존 전체 건수를 반환한다")
    void returnsEmptyPageWithCorrectCount() throws Exception {
        seed(userId, 1, CREATED_AT);

        JsonNode page = data(get("?page=1"));

        assertThat(page.path("content")).isEmpty();
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("page").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"totalPrice", "ordererId", "createdBy", "unknown"})
    @DisplayName("명세 밖의 정렬 필드는 빈 결과에서도 400 VALIDATION_ERROR로 거부한다")
    void rejectsDisallowedSort(String field) throws Exception {
        assertError(get("?sort=" + field + ",ASC"), 400, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("뒤집힌 날짜 범위는 400 INVALID_REQUEST로 반환한다")
    void rejectsReversedDates() throws Exception {
        assertError(get("?startDate=2026-10-16&endDate=2026-10-15"), 400, "INVALID_REQUEST");
    }

    @Test
    @DisplayName("주문자와 MASTER는 주문 스냅샷을 읽고 조회로 수정 시각이 바뀌지 않는다")
    void readsDetailAsOwnerAndMaster() throws Exception {
        UUID id = seed(userId, 50, CREATED_AT);

        JsonNode own = data(get("/" + id));
        JsonNode master = data(get("/" + id, UUID.randomUUID(), "MASTER"));

        assertThat(master).isEqualTo(own);
        assertThat(own.path("ordererId").asText()).isEqualTo(userId.toString());
        assertThat(own.path("productName").asText()).isEqualTo("주문 당시 상품");
        assertThat(own.path("unitPrice").asLong()).isEqualTo(12_000L);
        assertThat(own.path("totalPrice").asLong()).isEqualTo(600_000L);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM order_schema.p_orders WHERE order_id=?",
                LocalDateTime.class, id)).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("다른 USER의 단건 조회는 403 FORBIDDEN이다")
    void deniesOtherOwner() throws Exception {
        UUID id = seed(UUID.randomUUID(), 50, CREATED_AT);

        assertError(get("/" + id), 403, "FORBIDDEN");
    }

    @Test
    @DisplayName("없는 주문과 삭제된 주문은 모두 404 ORDER_NOT_FOUND다")
    void hidesMissingAndDeletedOrder() throws Exception {
        UUID id = seed(userId, 50, CREATED_AT);
        softDelete(id);

        assertError(get("/" + id), 404, "ORDER_NOT_FOUND");
        assertError(get("/" + UUID.randomUUID()), 404, "ORDER_NOT_FOUND");
    }

    private UUID seed(UUID owner, int quantity, LocalDateTime createdAt) {
        Order saved = orderRepository.saveAndFlush(Order.create(UUID.randomUUID(), owner, supplierId, receiverId,
                productId, "주문 당시 상품", 12_000L, quantity, UUID.randomUUID(), "오전 도착 희망"));
        fixtureOrderIds.add(saved.getId());
        jdbc.update("UPDATE order_schema.p_orders SET created_at=?, updated_at=? WHERE order_id=?", createdAt, createdAt, saved.getId());
        return saved.getId();
    }

    private void softDelete(UUID id) {
        jdbc.update("UPDATE order_schema.p_orders SET deleted_at=?, deleted_by='fixture' WHERE order_id=?", CREATED_AT, id);
    }

    private ResponseEntity<String> get(String suffix) {
        return get(suffix, userId, "USER");
    }

    private ResponseEntity<String> get(String suffix, UUID caller, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(UserHeaders.USER_ID, caller.toString());
        headers.set(UserHeaders.USERNAME, "query-user");
        headers.set(UserHeaders.ROLE, role);
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        return http.exchange("/api/v1/orders" + suffix, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return mapper.readTree(response.getBody()).path("data");
    }

    private void assertError(ResponseEntity<String> response, int status, String code) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo(code);
    }

    private List<String> ids(JsonNode page) {
        return StreamSupport.stream(page.path("content").spliterator(), false)
                .map(row -> row.path("orderId").asText()).toList();
    }
}
