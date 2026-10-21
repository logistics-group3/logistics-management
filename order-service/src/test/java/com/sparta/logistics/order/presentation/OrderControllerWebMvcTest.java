package com.sparta.logistics.order.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.OrderCancellationService;
import com.sparta.logistics.order.application.OrderCreationQueryService;
import com.sparta.logistics.order.application.OrderQueryService;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationOutcome;
import com.sparta.logistics.order.application.dto.OrderCreationProgress;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderStatus;
import com.sparta.logistics.order.infrastructure.config.OrderJacksonConfig;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = OrderController.class, properties = "spring.cloud.config.enabled=false")
@Import(OrderJacksonConfig.class)
class OrderControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private OrderQueryService orderQueryService;
    @MockitoBean
    private OrderCancellationService orderCancellationService;
    @MockitoBean
    private OrderCreationQueryService orderCreationQueryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    @DisplayName("주문 생성 요청을 접수하면 인증 사용자와 Command를 전달하고 202와 PROCESSING을 반환한다")
    void acceptsOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderService.accept(any(), any(), any())).willReturn(OrderCreationOutcome.accepted(orderId));

        mockMvc.perform(authenticatedPost(body("50", "오전 도착 희망")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.message").value("주문이 접수되었습니다. 처리 결과는 진행 조회로 확인하세요."))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        verify(orderService).accept(new UserContext(userId, "junhyun", Role.USER, false), "test-key",
                new CreateOrderCommand(supplierId, receiverId, productId, 50, "오전 도착 희망"));
    }

    @Test
    @DisplayName("같은 키 재요청이 이미 완료됐으면 최초 응답과 같은 201 본문을 반환한다")
    void returnsCreatedForCompletedReplay() throws Exception {
        OrderResult result = orderResult();
        given(orderService.accept(any(), any(), any())).willReturn(OrderCreationOutcome.completed(result));

        mockMvc.perform(authenticatedPost(body("50", "오전 도착 희망")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("주문이 생성되었습니다."))
                .andExpect(jsonPath("$.data.orderId").value(result.orderId().toString()))
                .andExpect(jsonPath("$.data.ordererId").value(userId.toString()))
                .andExpect(jsonPath("$.data.productName").value("마른오징어"))
                .andExpect(jsonPath("$.data.unitPrice").value(12_000))
                .andExpect(jsonPath("$.data.totalPrice").value(600_000))
                .andExpect(jsonPath("$.data.status").value("ORDERED"))
                .andExpect(jsonPath("$.data.deliveryId").value(result.deliveryId().toString()))
                .andExpect(jsonPath("$.data.createdAt").isString());
    }

    @Test
    @DisplayName("생성 진행 조회는 PROCESSING 상태를 반환한다")
    void returnsProcessingProgress() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderCreationQueryService.getProgress(any(), any()))
                .willReturn(new OrderCreationProgress(orderId, OrderCreationStatus.PROCESSING, null, null, null));

        mockMvc.perform(authenticatedGet(orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.order").doesNotExist());

        verify(orderCreationQueryService).getProgress(new UserContext(userId, "junhyun", Role.USER, false), orderId);
    }

    @Test
    @DisplayName("생성 진행 조회는 COMPLETED일 때 주문 본문을 포함한다")
    void returnsCompletedProgress() throws Exception {
        OrderResult result = orderResult();
        given(orderCreationQueryService.getProgress(any(), any()))
                .willReturn(new OrderCreationProgress(result.orderId(), OrderCreationStatus.COMPLETED, null, null, result));

        mockMvc.perform(authenticatedGet(result.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.order.orderId").value(result.orderId().toString()))
                .andExpect(jsonPath("$.data.order.status").value("ORDERED"))
                .andExpect(jsonPath("$.data.order.deliveryId").value(result.deliveryId().toString()));
    }

    @Test
    @DisplayName("생성 진행 조회는 ABORTED일 때 실패 코드와 메시지를 포함한다")
    void returnsAbortedProgress() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderCreationQueryService.getProgress(any(), any())).willReturn(new OrderCreationProgress(orderId,
                OrderCreationStatus.ABORTED, "SERVICE_UNAVAILABLE", "하위 서비스를 사용할 수 없습니다.", null));

        mockMvc.perform(authenticatedGet(orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ABORTED"))
                .andExpect(jsonPath("$.data.failureCode").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.failureMessage").value("하위 서비스를 사용할 수 없습니다."))
                .andExpect(jsonPath("$.data.order").doesNotExist());
    }

    @Test
    @DisplayName("생성 진행 조회는 RECOVERY_REQUIRED일 때 실패 사유를 포함한다")
    void returnsRecoveryRequiredProgress() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderCreationQueryService.getProgress(any(), any())).willReturn(new OrderCreationProgress(orderId,
                OrderCreationStatus.RECOVERY_REQUIRED, "ORDER_RECOVERY_REQUIRED", "운영자 복구가 필요합니다.", null));

        mockMvc.perform(authenticatedGet(orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECOVERY_REQUIRED"))
                .andExpect(jsonPath("$.data.failureCode").value("ORDER_RECOVERY_REQUIRED"))
                .andExpect(jsonPath("$.data.failureMessage").value("운영자 복구가 필요합니다."))
                .andExpect(jsonPath("$.data.order").doesNotExist());
    }

    @Test
    @DisplayName("생성 기록이 없으면 진행 조회는 404 ORDER_CREATION_NOT_FOUND이다")
    void returnsNotFoundForUnknownCreation() throws Exception {
        given(orderCreationQueryService.getProgress(any(), any()))
                .willThrow(new BusinessException(OrderErrorCode.ORDER_CREATION_NOT_FOUND));

        mockMvc.perform(authenticatedGet(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_CREATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("주문자가 아니면 진행 조회는 403이다")
    void returnsForbiddenForOtherUserProgress() throws Exception {
        given(orderCreationQueryService.getProgress(any(), any())).willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        mockMvc.perform(authenticatedGet(UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5"})
    @DisplayName("수량이 없거나 양의 정수가 아니면 400이고 서비스를 호출하지 않는다")
    void rejectsInvalidQuantity(String quantity) throws Exception {
        mockMvc.perform(authenticatedPost(body(quantity, "요청사항")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("필수 ID가 없으면 필드 오류를 포함한 400으로 거부한다")
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(authenticatedPost("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("UUID 형식이 잘못되면 400이고 서비스를 호출하지 않는다")
    void rejectsMalformedId() throws Exception {
        mockMvc.perform(authenticatedPost(body("50", "요청사항").replace(productId.toString(), "invalid-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("요청사항이 500자를 넘으면 400으로 거부한다")
    void rejectsLongRequestNote() throws Exception {
        mockMvc.perform(authenticatedPost(body("50", "가".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("인증 사용자 헤더가 없으면 401이고 서비스를 호출하지 않는다")
    void requiresCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body("50", "요청사항")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("서비스의 소유자 검증 실패를 공통 403 응답으로 전달한다")
    void preservesForbidden() throws Exception {
        given(orderService.accept(any(), any(), any())).willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        mockMvc.perform(authenticatedPost(body("50", "요청사항")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("하위 서비스 장애를 공통 503 응답으로 전달한다")
    void preservesUnavailable() throws Exception {
        given(orderService.accept(any(), any(), any())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));
        mockMvc.perform(authenticatedPost(body("50", "요청사항")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("멱등키가 없으면 생성 서비스를 호출하지 않고 400이다")
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(authenticatedPost(body("50", "요청사항"), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "key,key", "한글", "key/key"})
    @DisplayName("빈 키·공백·허용되지 않은 문자의 키는 400이다")
    void rejectsInvalidKey(String key) throws Exception {
        mockMvc.perform(authenticatedPost(body("50", "요청사항"), key))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("129자 키와 여러 개의 키 헤더는 거부한다")
    void rejectsLongAndRepeatedHeaders() throws Exception {
        mockMvc.perform(authenticatedPost(body("50", "요청사항"), "a".repeat(129)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authenticatedPost(body("50", "요청사항")).header("Idempotency-Key", "second-key"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    private OrderResult orderResult() {
        return new OrderResult(UUID.randomUUID(), userId, supplierId, receiverId, productId,
                "마른오징어", 12_000L, 50, 600_000L, "오전 도착 희망", OrderStatus.ORDERED, UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 15, 12, 0));
    }

    private MockHttpServletRequestBuilder authenticatedGet(UUID orderId) {
        return get("/api/v1/orders/creations/{orderId}", orderId)
                .header(UserHeaders.USER_ID, userId.toString())
                .header(UserHeaders.USERNAME, "junhyun")
                .header(UserHeaders.ROLE, "USER")
                .header(UserHeaders.DELIVERY_MANAGER, "false");
    }

    private MockHttpServletRequestBuilder authenticatedPost(String body) {
        return authenticatedPost(body, "test-key");
    }

    private MockHttpServletRequestBuilder authenticatedPost(String body, String key) {
        MockHttpServletRequestBuilder request = post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(UserHeaders.USER_ID, userId.toString())
                .header(UserHeaders.USERNAME, "junhyun")
                .header(UserHeaders.ROLE, "USER")
                .header(UserHeaders.DELIVERY_MANAGER, "false");
        if (key != null) request.header("Idempotency-Key", key);
        return request;
    }

    private String body(String quantity, String requestNote) {
        return """
                {"supplierCompanyId":"%s","receiverCompanyId":"%s","productId":"%s",
                 "quantity":%s,"requestNote":"%s"}
                """.formatted(supplierId, receiverId, productId, quantity, requestNote);
    }
}
