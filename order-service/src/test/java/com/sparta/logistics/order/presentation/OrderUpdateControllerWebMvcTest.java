package com.sparta.logistics.order.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.order.application.OrderQueryService;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.infrastructure.config.OrderJacksonConfig;
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
class OrderUpdateControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private OrderQueryService orderQueryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    @DisplayName("PATCH는 인증 사용자·주문 ID·요청사항을 전달하고 수정된 OrderResponse를 반환한다")
    void updatesRequestNote() throws Exception {
        Order order = Order.create(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "오후 도착 희망");
        given(orderService.updateRequestNote(any(), any(), any())).willReturn(OrderResult.from(order));

        mockMvc.perform(authenticated(orderId.toString()).content("{\"requestNote\":\"오후 도착 희망\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.requestNote").value("오후 도착 희망"))
                .andExpect(jsonPath("$.data.totalPrice").value(600_000));

        verify(orderService).updateRequestNote(new UserContext(userId, "junhyun", Role.USER, false), orderId,
                new UpdateOrderCommand("오후 도착 희망"));
        verifyNoInteractions(orderQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"requestNote\":null}", "{\"requestNote\":\"\"}", "{\"requestNote\":\"   \"}",
            "{\"requestNote\":[]}", "{broken", ""})
    @DisplayName("필수 요청사항 누락·공백·잘못된 본문은 서비스 호출 전에 400으로 거부한다")
    void rejectsInvalidBody(String body) throws Exception {
        mockMvc.perform(authenticated(orderId.toString()).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderService, orderQueryService);
    }

    @Test
    @DisplayName("501자 요청사항은 필드 오류를 반환한다")
    void rejectsLongNote() throws Exception {
        mockMvc.perform(authenticated(orderId.toString()).content("{\"requestNote\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("requestNote"));
        verifyNoInteractions(orderService, orderQueryService);
    }

    @Test
    @DisplayName("잘못된 주문 UUID는 400이며 서비스에 전달하지 않는다")
    void rejectsMalformedId() throws Exception {
        mockMvc.perform(authenticated("invalid").content("{\"requestNote\":\"변경 요청\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService, orderQueryService);
    }

    @Test
    @DisplayName("사용자 정보가 없는 수정은 401이다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/" + orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNote\":\"변경 요청\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService, orderQueryService);
    }

    private MockHttpServletRequestBuilder authenticated(String id) {
        return patch("/api/v1/orders/" + id).contentType(MediaType.APPLICATION_JSON)
                .header(UserHeaders.USER_ID, userId).header(UserHeaders.USERNAME, "junhyun")
                .header(UserHeaders.ROLE, "USER").header(UserHeaders.DELIVERY_MANAGER, "false");
    }
}
