package com.sparta.logistics.hub.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.StockRegistrationService;
import com.sparta.logistics.hub.application.StockService;
import com.sparta.logistics.hub.application.dto.AllocateStockCommand;
import com.sparta.logistics.hub.application.dto.InitializeStockCommand;
import com.sparta.logistics.hub.application.dto.InternalStockResult;
import com.sparta.logistics.hub.application.dto.StockAllocationResult;
import com.sparta.logistics.hub.application.dto.StockRestoreResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = InternalStockController.class, properties = "spring.cloud.config.enabled=false")
class InternalStockControllerWebMvcTest {

    private final UUID hubId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID hubStockId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockRegistrationService stockRegistrationService;
    @MockitoBean
    private StockService stockService;

    private MockHttpServletRequestBuilder json(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @DisplayName("I-04는 재고를 만들고 감싸지 않은 hubStockId와 수량을 반환한다")
    void initialize() throws Exception {
        given(stockRegistrationService.initialize(new InitializeStockCommand(hubId, productId)))
                .willReturn(new InternalStockResult(hubStockId, 0));

        mockMvc.perform(json("/internal/stocks", "{\"hubId\":\"" + hubId + "\",\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubStockId").value(hubStockId.toString()))
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("I-04에서 허브 ID나 상품 ID가 없으면 400 VALIDATION_ERROR")
    void initializeValidation() throws Exception {
        mockMvc.perform(json("/internal/stocks", "{\"hubId\":\"" + hubId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(stockRegistrationService, never()).initialize(any());
    }

    @Test
    @DisplayName("I-04에서 허브가 없으면 404 HUB_NOT_FOUND")
    void initializeHubNotFound() throws Exception {
        given(stockRegistrationService.initialize(any())).willThrow(new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        mockMvc.perform(json("/internal/stocks", "{\"hubId\":\"" + hubId + "\",\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"));
    }

    @Test
    @DisplayName("I-05는 공급업체 허브에서 차감한 허브와 수량을 감싸지 않고 반환한다")
    void allocate() throws Exception {
        given(stockService.allocate(new AllocateStockCommand(orderId, productId, 4, hubId)))
                .willReturn(new StockAllocationResult(hubId, 4));

        mockMvc.perform(json("/internal/stocks/allocate", allocateBody(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("I-05에서 재고가 부족하면 409 STOCK_INSUFFICIENT")
    void allocateInsufficient() throws Exception {
        given(stockService.allocate(any())).willThrow(new BusinessException(HubErrorCode.STOCK_INSUFFICIENT));

        mockMvc.perform(json("/internal/stocks/allocate", allocateBody(4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INSUFFICIENT"));
    }

    @Test
    @DisplayName("I-05에서 수량이 1 미만이거나 필수 값이 없으면 400 VALIDATION_ERROR")
    void allocateValidation() throws Exception {
        mockMvc.perform(json("/internal/stocks/allocate", allocateBody(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(json("/internal/stocks/allocate",
                        "{\"orderId\":\"" + orderId + "\",\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(stockService, never()).allocate(any());
    }

    @Test
    @DisplayName("I-06은 되돌린 수량을 감싸지 않고 반환한다")
    void restore() throws Exception {
        given(stockService.restore(orderId)).willReturn(new StockRestoreResult(4));

        mockMvc.perform(json("/internal/stocks/restore", "{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restoredQuantity").value(4))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("I-06에서 주문 ID가 없으면 400 VALIDATION_ERROR")
    void restoreValidation() throws Exception {
        mockMvc.perform(json("/internal/stocks/restore", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(stockService, never()).restore(any());
    }

    private String allocateBody(int quantity) {
        return "{\"orderId\":\"" + orderId + "\",\"productId\":\"" + productId
                + "\",\"quantity\":" + quantity + ",\"supplierHubId\":\"" + hubId + "\"}";
    }
}
