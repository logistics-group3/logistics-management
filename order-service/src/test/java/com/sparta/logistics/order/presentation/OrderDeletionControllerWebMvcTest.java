package com.sparta.logistics.order.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.OrderDeletionService;
import com.sparta.logistics.order.domain.OrderErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = OrderDeletionController.class, properties = "spring.cloud.config.enabled=false")
class OrderDeletionControllerWebMvcTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderDeletionService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    @DisplayName("MASTER의 DELETE는 200과 data null을 반환하고 사용자 정보를 전달한다")
    void deletes() throws Exception {
        mvc.perform(request(orderId.toString(), "MASTER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        verify(service).delete(new UserContext(userId, "master", Role.MASTER, false), orderId);
    }

    @Test
    @DisplayName("USER는 주문 소유자여도 Controller에서 403으로 거부한다")
    void deniesUser() throws Exception {
        mvc.perform(request(orderId.toString(), "USER")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401이다")
    void requiresAuthentication() throws Exception {
        mvc.perform(delete("/api/v1/orders/" + orderId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("잘못된 주문 UUID는 서비스 실행 전에 400이다")
    void validatesId() throws Exception {
        mvc.perform(request("invalid", "MASTER")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_NOT_FOUND", "ORDER_NOT_DELETABLE", "SERVICE_UNAVAILABLE", "INTERNAL_SERVER_ERROR"})
    @DisplayName("삭제 업무 오류는 공통 오류 응답의 상태와 코드를 유지한다")
    void preservesError(String code) throws Exception {
        BusinessException failure = code.startsWith("ORDER_") ? new BusinessException(OrderErrorCode.valueOf(code))
                : new BusinessException(CommonErrorCode.valueOf(code));
        willThrow(failure).given(service).delete(any(), any());
        mvc.perform(request(orderId.toString(), "MASTER")).andExpect(status().is(failure.getErrorCode().status().value()))
                .andExpect(jsonPath("$.code").value(code));
    }

    private MockHttpServletRequestBuilder request(String id, String role) {
        return delete("/api/v1/orders/" + id).header(UserHeaders.USER_ID, userId.toString())
                .header(UserHeaders.USERNAME, "master").header(UserHeaders.ROLE, role).header(UserHeaders.DELIVERY_MANAGER, "false");
    }
}
