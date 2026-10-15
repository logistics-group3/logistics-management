package com.sparta.logistics.order.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.OrderQueryService;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderResult;
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

    private final UUID userId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    @DisplayName("주문 생성 요청을 인증 사용자와 Command로 전달하고 SA 형식의 201 응답을 반환한다")
    void createsOrder() throws Exception {
        OrderResult result = new OrderResult(UUID.randomUUID(), userId, supplierId, receiverId, productId,
                "마른오징어", 12_000L, 50, 600_000L, "오전 도착 희망", OrderStatus.ORDERED, UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 15, 12, 0));
        given(orderService.create(any(), any())).willReturn(result);

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

        verify(orderService).create(new UserContext(userId, "junhyun", Role.USER, false),
                new CreateOrderCommand(supplierId, receiverId, productId, 50, "오전 도착 희망"));
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
        given(orderService.create(any(), any())).willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        mockMvc.perform(authenticatedPost(body("50", "요청사항")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("하위 서비스 장애를 공통 503 응답으로 전달한다")
    void preservesUnavailable() throws Exception {
        given(orderService.create(any(), any())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));
        mockMvc.perform(authenticatedPost(body("50", "요청사항")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    private MockHttpServletRequestBuilder authenticatedPost(String body) {
        return post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(UserHeaders.USER_ID, userId.toString())
                .header(UserHeaders.USERNAME, "junhyun")
                .header(UserHeaders.ROLE, "USER")
                .header(UserHeaders.DELIVERY_MANAGER, "false");
    }

    private String body(String quantity, String requestNote) {
        return """
                {"supplierCompanyId":"%s","receiverCompanyId":"%s","productId":"%s",
                 "quantity":%s,"requestNote":"%s"}
                """.formatted(supplierId, receiverId, productId, quantity, requestNote);
    }
}
