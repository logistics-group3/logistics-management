package com.sparta.logistics.hub.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.hub.application.StockQueryService;
import com.sparta.logistics.hub.application.StockRegistrationService;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = HubStockController.class, properties = "spring.cloud.config.enabled=false")
class HubStockControllerWebMvcTest {

    private static final String USER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    private final UUID hubId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID hubStockId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockQueryService stockQueryService;
    @MockitoBean
    private StockRegistrationService stockRegistrationService;

    private MockHttpServletRequestBuilder asRole(MockHttpServletRequestBuilder request, String role) {
        return request.header(UserHeaders.USER_ID, USER_ID)
                .header(UserHeaders.USERNAME, "hong01")
                .header(UserHeaders.ROLE, role);
    }

    private MockHttpServletRequestBuilder inbound(String role, String body) {
        return asRole(post("/api/v1/hubs/{hubId}/stocks/inbound", hubId), role)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    @DisplayName("H-05는 허브의 재고 목록을 ApiResponse 페이지로 반환한다")
    void search() throws Exception {
        StockResult stock = new StockResult(hubStockId, hubId, productId, 450, LocalDateTime.of(2026, 10, 14, 11, 20));
        given(stockQueryService.search(eq(hubId), eq(productId), any()))
                .willReturn(new PageImpl<>(List.of(stock), PageRequest.of(0, 10), 1));

        mockMvc.perform(asRole(get("/api/v1/hubs/{hubId}/stocks", hubId), "USER").param("productId", productId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].hubStockId").value(hubStockId.toString()))
                .andExpect(jsonPath("$.data.content[0].hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data.content[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.data.content[0].quantity").value(450))
                .andExpect(jsonPath("$.data.content[0].updatedAt").value("2026-10-14T11:20:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("H-05에서 허브가 없으면 404 HUB_NOT_FOUND")
    void searchHubNotFound() throws Exception {
        given(stockQueryService.search(eq(hubId), any(), any())).willThrow(new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        mockMvc.perform(asRole(get("/api/v1/hubs/{hubId}/stocks", hubId), "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"));
    }

    @Test
    @DisplayName("H-05에서 productId가 UUID가 아니면 400 VALIDATION_ERROR")
    void searchInvalidProductId() throws Exception {
        mockMvc.perform(asRole(get("/api/v1/hubs/{hubId}/stocks", hubId), "USER").param("productId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("H-06은 MASTER가 입고하면 입고 후 수량을 메시지와 함께 반환한다")
    void inboundByMaster() throws Exception {
        given(stockRegistrationService.inbound(any(), eq(new InboundStockCommand(hubId, productId, 500))))
                .willReturn(new StockResult(hubStockId, hubId, productId, 500, LocalDateTime.now()));

        mockMvc.perform(inbound("MASTER", "{\"productId\":\"" + productId + "\",\"quantity\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("재고가 입고되었습니다."))
                .andExpect(jsonPath("$.data.hubStockId").value(hubStockId.toString()))
                .andExpect(jsonPath("$.data.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                .andExpect(jsonPath("$.data.quantity").value(500))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("H-06은 USER도 컨트롤러에서 막지 않고 서비스가 업체 관리자 여부를 판단한다")
    void inboundByUser() throws Exception {
        given(stockRegistrationService.inbound(any(), eq(new InboundStockCommand(hubId, productId, 5))))
                .willReturn(new StockResult(hubStockId, hubId, productId, 5, LocalDateTime.now()));

        mockMvc.perform(inbound("USER", "{\"productId\":\"" + productId + "\",\"quantity\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H-06은 업체 관리자가 아니면 서비스가 던진 403 FORBIDDEN을 반환한다")
    void inboundByNonManagerForbidden() throws Exception {
        given(stockRegistrationService.inbound(any(), any())).willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        mockMvc.perform(inbound("USER", "{\"productId\":\"" + productId + "\",\"quantity\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("H-06은 사용자 헤더가 없으면 401")
    void inboundWithoutHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/hubs/{hubId}/stocks/inbound", hubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("H-06은 수량이 1 미만이거나 상품 ID가 없으면 400 VALIDATION_ERROR")
    void inboundValidation() throws Exception {
        mockMvc.perform(inbound("MASTER", "{\"productId\":\"" + productId + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(inbound("MASTER", "{\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(inbound("MASTER", "{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(stockRegistrationService, never()).inbound(any(), any());
    }

    @Test
    @DisplayName("H-06에서 허브가 없으면 404 HUB_NOT_FOUND")
    void inboundHubNotFound() throws Exception {
        given(stockRegistrationService.inbound(any(), any())).willThrow(new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        mockMvc.perform(inbound("MASTER", "{\"productId\":\"" + productId + "\",\"quantity\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"));
    }
}
