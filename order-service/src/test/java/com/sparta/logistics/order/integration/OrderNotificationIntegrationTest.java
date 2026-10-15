package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 실제 HTTP 주문·DB 커밋·비동기 프록시·Webhook을 연결한다. Slack은 루프백 HTTP 서버로만 대체한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"
})
@TestPropertySource(properties = {
        "spring.flyway.schemas=order_schema",
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.default_schema=order_schema",
        "slack.connect-timeout=1s", "slack.read-timeout=2s"
})
@ExtendWith(OutputCaptureExtension.class)
class OrderNotificationIntegrationTest extends PostgresIntegrationTest {

    private static final HttpStubServer SLACK = new HttpStubServer();
    private static final String WEBHOOK_PATH = "/services/test-secret-token";

    @DynamicPropertySource
    static void configureSlack(DynamicPropertyRegistry registry) {
        registry.add("slack.webhook-url", () -> SLACK.url() + WEBHOOK_PATH);
    }

    @AfterAll
    static void closeStub() {
        SLACK.close();
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    @Qualifier("orderNotificationExecutor")
    private ThreadPoolTaskExecutor executor;
    @MockitoBean
    private CompanyReader companyReader;
    @MockitoBean
    private DeliveryGateway deliveryGateway;

    private final UUID userId = UUID.randomUUID();
    private final CompanyResult supplier = new CompanyResult(UUID.randomUUID(), "공급 & 업체", UUID.randomUUID(),
            "서울", UUID.randomUUID());
    private final CompanyResult receiver = new CompanyResult(UUID.randomUUID(), "수령업체", UUID.randomUUID(), "부산", userId);
    private final ProductResult product = new ProductResult(UUID.randomUUID(), supplier.companyId(), "마른오징어", 12_000L);

    @BeforeEach
    void setUp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(10_000);
        http.getRestTemplate().setRequestFactory(factory);
        given(companyReader.getCompany(supplier.companyId())).willReturn(supplier);
        given(companyReader.getCompany(receiver.companyId())).willReturn(receiver);
        given(companyReader.getProduct(product.productId())).willReturn(product);
        given(deliveryGateway.create(any())).willReturn(UUID.randomUUID());
        SLACK.reset(request -> Reply.json(200, "ok"));
    }

    @AfterEach
    void finishAsyncWork() {
        awaitIdle();
    }

    @Test
    @DisplayName("별도 연결에서 커밋된 주문을 확인하고 Slack 응답 전에도 주문은 201을 반환한다")
    void sendsCommittedSnapshotWithoutBlockingResponse() throws Exception {
        CountDownLatch webhookEntered = new CountDownLatch(1);
        CountDownLatch releaseWebhook = new CountDownLatch(1);
        AtomicInteger committedRows = new AtomicInteger(-1);
        SLACK.reset(request -> {
            try {
                String text = mapper.readTree(request.body()).path("text").asText();
                UUID orderId = UUID.fromString(text.lines().findFirst().orElseThrow().substring("[신규 주문] ".length()));
                committedRows.set(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?",
                        Integer.class, orderId));
                webhookEntered.countDown();
                releaseWebhook.await(5, TimeUnit.SECONDS);
                return Reply.json(200, "ok");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        try {
            UUID orderId = assertCreated(create());
            assertThat(webhookEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(committedRows.get()).isEqualTo(1);
            // Slack 응답을 아직 열지 않았고 작업 스레드는 전송 중인데, 주문 HTTP 응답은 이미 받았다.
            assertThat(releaseWebhook.getCount()).isEqualTo(1);
            assertThat(executor.getActiveCount()).isEqualTo(1);
            var request = SLACK.requests("POST", WEBHOOK_PATH).get(0);
            JsonNode body = mapper.readTree(request.body());
            assertThat(body.path("text").asText()).isEqualTo("[신규 주문] " + orderId
                    + "\n공급업체: 공급 &amp; 업체 → 수령업체: 수령업체\n상품: 마른오징어 x 50"
                    + "\n요청사항: &lt;!channel&gt; 오전 도착 희망");
            assertThat(body.path("mrkdwn").asBoolean(true)).isFalse();
            assertThat(request.header("Content-Type")).startsWith("application/json");
            assertThat(request.header("Authorization")).isNull();
            for (String header : UserHeaders.ALL) {
                assertThat(request.header(header)).isNull();
            }
        } finally {
            releaseWebhook.countDown();
        }
        awaitIdle();
        assertThat(SLACK.requests("POST", WEBHOOK_PATH)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500})
    @DisplayName("Slack 오류는 주문 201을 유지하고 재시도·배송 보상 없이 비밀값을 제외해 기록한다")
    void keepsOrderWhenWebhookFails(int status, CapturedOutput output) throws Exception {
        SLACK.reset(request -> Reply.json(status, "error: " + SLACK.url() + WEBHOOK_PATH));

        UUID orderId = assertCreated(create());
        awaitIdle();

        assertThat(SLACK.requests("POST", WEBHOOK_PATH)).hasSize(1);
        assertThat(output.getAll()).contains(orderId.toString(), "status=SLACK_NOTIFICATION_FAILED", "httpStatus=" + status)
                .doesNotContain(WEBHOOK_PATH, "test-secret-token");
    }

    @Test
    @DisplayName("Slack 타임아웃에도 주문은 성공하고 Webhook URL을 로그에 남기거나 재시도하지 않는다")
    void keepsOrderWhenWebhookTimesOut(CapturedOutput output) throws Exception {
        SLACK.reset(request -> Reply.json(200, "ok").delayed(3000));

        UUID orderId = assertCreated(create());
        awaitIdle();

        assertThat(SLACK.requests("POST", WEBHOOK_PATH)).hasSize(1);
        assertThat(output.getAll()).contains(orderId.toString(), "reason=ResourceAccessException")
                .doesNotContain(WEBHOOK_PATH, "test-secret-token");
    }

    @Test
    @DisplayName("알림 스레드와 큐가 포화되어도 주문은 커밋되고 201을 반환하며 배송을 보상하지 않는다")
    void keepsOrderWhenExecutorRejectsNotification(CapturedOutput output) throws Exception {
        CountDownLatch workersStarted = new CountDownLatch(executor.getMaxPoolSize());
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        try {
            for (int index = 0; index < executor.getMaxPoolSize(); index++) {
                executor.execute(() -> {
                    workersStarted.countDown();
                    try {
                        releaseWorkers.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(workersStarted.await(3, TimeUnit.SECONDS)).isTrue();
            int remaining = executor.getThreadPoolExecutor().getQueue().remainingCapacity();
            for (int index = 0; index < remaining; index++) {
                executor.execute(() -> { });
            }

            UUID orderId = assertCreated(create());

            assertThat(SLACK.requests("POST", WEBHOOK_PATH)).isEmpty();
            assertThat(output.getAll()).contains(orderId.toString(), "status=NOTIFICATION_SUBMISSION_FAILED",
                    "reason=TaskRejectedException");
        } finally {
            releaseWorkers.countDown();
        }
    }

    private ResponseEntity<String> create() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(UserHeaders.USER_ID, userId.toString());
        headers.set(UserHeaders.USERNAME, "junhyun");
        headers.set(UserHeaders.ROLE, "USER");
        headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(Map.of(
                "supplierCompanyId", supplier.companyId(), "receiverCompanyId", receiver.companyId(),
                "productId", product.productId(), "quantity", 50, "requestNote", "<!channel> 오전 도착 희망"), headers), String.class);
    }

    private UUID assertCreated(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = UUID.fromString(mapper.readTree(response.getBody()).path("data").path("orderId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        verify(deliveryGateway, never()).deleteByOrderId(any());
        return orderId;
    }

    private void awaitIdle() {
        await().atMost(Duration.ofSeconds(6)).until(() -> executor.getActiveCount() == 0
                && executor.getThreadPoolExecutor().getQueue().isEmpty());
    }
}
