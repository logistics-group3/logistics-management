package com.sparta.logistics.order.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.order.application.OrderQueryService;
import com.sparta.logistics.order.application.OrderService;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.OrderSearchCondition;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.infrastructure.config.OrderJacksonConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = OrderController.class, properties = "spring.cloud.config.enabled=false")
@Import(OrderJacksonConfig.class)
class OrderQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private OrderQueryService orderQueryService;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("검색 요청은 날짜 조건·기본 페이지를 전달하고 공통 페이지 응답으로 변환한다")
    void mapsSearchAndPageResponse() throws Exception {
        OrderResult order = result();
        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        given(orderQueryService.search(any(), any(), any())).willReturn(new PageImpl<>(List.of(order), page, 14));

        mockMvc.perform(authenticated("/api/v1/orders?startDate=2026-10-01&endDate=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId").value(order.orderId().toString()))
                .andExpect(jsonPath("$.data.content[0].totalPrice").value(600_000))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(14))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.sort").value("createdAt,DESC"));

        verify(orderQueryService).search(new UserContext(userId, "junhyun", Role.USER, false),
                new OrderSearchCondition(null, null, null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), page);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("단건 조회는 인증 사용자와 UUID를 전달하고 OrderResponse로 반환한다")
    void mapsDetailResponse() throws Exception {
        OrderResult order = result();
        given(orderQueryService.getOrder(any(), any())).willReturn(order);

        mockMvc.perform(authenticated("/api/v1/orders/" + order.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("주문 당시 상품"))
                .andExpect(jsonPath("$.data.deliveryId").value(order.deliveryId().toString()))
                .andExpect(jsonPath("$.data.status").value("ORDERED"));
        verify(orderQueryService).getOrder(new UserContext(userId, "junhyun", Role.USER, false), order.orderId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"status=INVALID", "supplierCompanyId=bad", "receiverCompanyId=bad", "productId=bad",
            "startDate=2026-10-15T00:00:00", "startDate=2026-02-30", "endDate=not-a-date"})
    @DisplayName("형식이 잘못된 검색 조건은 400으로 처리하고 조회 서비스를 호출하지 않는다")
    void rejectsInvalidSearchParameters(String query) throws Exception {
        mockMvc.perform(authenticated("/api/v1/orders?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderQueryService, orderService);
    }

    @Test
    @DisplayName("잘못된 주문 UUID는 공통 400 응답을 반환한다")
    void rejectsInvalidOrderId() throws Exception {
        mockMvc.perform(authenticated("/api/v1/orders/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orderQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/orders", "/api/v1/orders/00000000-0000-0000-0000-000000000001"})
    @DisplayName("조회 API는 인증 사용자 헤더가 없으면 401을 반환한다")
    void rejectsUnauthenticatedReads(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderQueryService, orderService);
    }

    private MockHttpServletRequestBuilder authenticated(String path) {
        return get(path).header(UserHeaders.USER_ID, userId.toString()).header(UserHeaders.USERNAME, "junhyun")
                .header(UserHeaders.ROLE, "USER").header(UserHeaders.DELIVERY_MANAGER, "false");
    }

    private OrderResult result() {
        return OrderResult.from(Order.create(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), null));
    }
}
