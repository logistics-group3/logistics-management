package com.sparta.logistics.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.DeliveryGateway;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.application.notification.NotificationDirectory;
import com.sparta.logistics.order.support.HttpStubServer;
import com.sparta.logistics.order.support.HttpStubServer.Reply;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.schemas=order_schema", "spring.jpa.properties.hibernate.default_schema=order_schema", "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0",
        "slack.dm.enabled=true", "slack.dm.bot-token=ui-test-dm-secret", "slack.dm.workspace-id=TTEST", "slack.webhook-url="
})
@ExtendWith(OutputCaptureExtension.class)
class OrderDmNotificationIntegrationTest extends PostgresIntegrationTest {
    private static final HttpStubServer SLACK = new HttpStubServer();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("slack.dm.api-base-url", SLACK::url); }
    @AfterAll static void closeStub() { SLACK.close(); }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactions;
    @Autowired @Qualifier("orderNotificationExecutor") ThreadPoolTaskExecutor executor;
    @MockitoBean CompanyReader companies;
    @MockitoBean DeliveryGateway deliveries;
    @MockitoBean NotificationDirectory directory;
    private final UUID userId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final CompanyResult supplier = new CompanyResult(UUID.randomUUID(), "공급", UUID.randomUUID(), "서울", UUID.randomUUID());
    private final CompanyResult receiver = new CompanyResult(UUID.randomUUID(), "수령", UUID.randomUUID(), "서울", userId);
    private final ProductResult product = new ProductResult(UUID.randomUUID(), supplier.companyId(), "DM 테스트 상품", 1000L);
    private final AtomicBoolean outsideTransaction = new AtomicBoolean();

    @BeforeEach void setUp() {
        given(companies.getCompany(supplier.companyId())).willReturn(supplier);
        given(companies.getCompany(receiver.companyId())).willReturn(receiver);
        given(companies.getProduct(product.productId())).willReturn(product);
        given(deliveries.create(any())).willReturn(deliveryId);
        given(directory.findTargets(deliveryId)).willAnswer(invocation -> {
            outsideTransaction.set(!TransactionSynchronizationManager.isActualTransactionActive());
            UUID id = jdbc.queryForObject("SELECT order_id FROM order_schema.p_orders WHERE delivery_id=?", UUID.class, deliveryId);
            return new NotificationDirectory.Targets(deliveryId, id, "PICKUP_WAITING", List.of(managerId, managerId));
        });
        given(directory.findContact(managerId)).willReturn(new NotificationDirectory.Contact(managerId, true, "driver+test@example.com"));
        SLACK.reset(this::success);
    }
    @AfterEach void finish() { awaitIdle(); }

    @Test @DisplayName("DM은 DB 커밋 후 별도 스레드에서 실행되며 발송 응답 전에도 주문 201을 돌려준다")
    void sendsAfterCommitWithoutBlocking() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SLACK.reset(request -> {
            if (request.path().endsWith("chat.postMessage")) {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return success(request);
        });
        try {
            UUID id = created(create(UUID.randomUUID().toString()));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(outsideTransaction).isTrue();
            var request = SLACK.requests("POST", "/chat.postMessage").get(0);
            assertThat(mapper.readTree(request.body()).path("text").asText()).contains(id.toString(), "&lt;!channel&gt;");
            for (String name : UserHeaders.ALL) assertThat(request.header(name)).isNull();
        } finally { release.countDown(); }
    }

    @Test @DisplayName("같은 Idempotency-Key 재요청은 같은 주문을 반환하고 DM을 다시 보내지 않는다")
    void replayDoesNotNotifyAgain() throws Exception {
        String key = UUID.randomUUID().toString();
        UUID first = created(create(key));
        assertThat(created(create(key))).isEqualTo(first);
        awaitIdle();
        assertThat(SLACK.requests("POST", "/chat.postMessage")).hasSize(1);
        verify(directory, times(1)).findTargets(deliveryId);
    }

    @Test @DisplayName("Slack 200/ok=false에도 주문은 유지되고 실패 로그에 토큰·이메일·응답 원문을 넣지 않는다")
    void isolatesFailure(CapturedOutput output) throws Exception {
        SLACK.reset(request -> request.path().endsWith("chat.postMessage")
                ? Reply.json(200, "{\"ok\":false,\"error\":\"missing_scope\",\"private\":\"ui-test-dm-secret driver+test@example.com\"}") : success(request));
        UUID id = created(create(UUID.randomUUID().toString()));
        awaitIdle();
        assertThat(SLACK.requests("POST", "/chat.postMessage")).hasSize(1);
        assertThat(output.getAll()).contains(id.toString(), "PERMISSION_DENIED").doesNotContain("ui-test-dm-secret", "driver+test@example.com");
        verify(deliveries, never()).deleteByOrderId(any());
    }

    @Test @DisplayName("롤백한 트랜잭션과 트랜잭션 밖 이벤트는 DM을 발송하지 않는다")
    void ignoresRollbackAndNoTransaction() {
        var event = new OrderCreatedEvent(UUID.randomUUID(), "공급", "수령", "상품", 1, null, deliveryId);
        new TransactionTemplate(transactions).executeWithoutResult(status -> { publisher.publishEvent(event); status.setRollbackOnly(); });
        publisher.publishEvent(event);
        awaitIdle();
        verifyNoInteractions(directory);
        assertThat(SLACK.requests("GET", "/")).isEmpty();
    }

    private ResponseEntity<String> create(String key) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key); headers.set(UserHeaders.USER_ID, userId.toString());
        headers.set(UserHeaders.USERNAME, "dmuser"); headers.set(UserHeaders.ROLE, "USER"); headers.set(UserHeaders.DELIVERY_MANAGER, "false");
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(Map.of("supplierCompanyId", supplier.companyId(), "receiverCompanyId", receiver.companyId(), "productId", product.productId(), "quantity", 1, "requestNote", "<!channel> 테스트"), headers), String.class);
    }
    private UUID created(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(mapper.readTree(response.getBody()).path("data").path("orderId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_schema.p_orders WHERE order_id=?", Integer.class, id)).isEqualTo(1);
        return id;
    }
    private Reply success(HttpStubServer.RecordedRequest request) {
        return switch (request.path()) {
            case "/users.lookupByEmail" -> Reply.json(200, "{\"ok\":true,\"user\":{\"id\":\"UTEST1\",\"team_id\":\"TTEST\",\"deleted\":false,\"is_bot\":false}}");
            case "/conversations.open" -> Reply.json(200, "{\"ok\":true,\"channel\":{\"id\":\"DTEST1\"}}");
            case "/chat.postMessage" -> Reply.json(200, "{\"ok\":true,\"channel\":\"DTEST1\",\"ts\":\"100.001\"}");
            default -> Reply.json(404, "{}");
        };
    }
    private void awaitIdle() { await().atMost(Duration.ofSeconds(6)).until(() -> executor.getActiveCount() == 0 && executor.getThreadPoolExecutor().getQueue().isEmpty()); }
}
