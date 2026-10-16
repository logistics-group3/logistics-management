package com.sparta.logistics.hub.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.HubRouteQueryService;
import com.sparta.logistics.hub.application.dto.InternalHubRouteResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InternalHubRouteController.class, properties = "spring.cloud.config.enabled=false")
class InternalHubRouteControllerWebMvcTest {

    private final UUID hubRouteId = UUID.randomUUID();
    private final UUID departureHubId = UUID.randomUUID();
    private final UUID arrivalHubId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubRouteQueryService hubRouteQueryService;

    @Test
    @DisplayName("I-03은 ApiResponse로 감싸지 않고 hubRouteId·distanceKm·durationMinutes만 반환한다")
    void getHubRoute() throws Exception {
        given(hubRouteQueryService.getInternalHubRoute(departureHubId, arrivalHubId))
                .willReturn(new InternalHubRouteResult(hubRouteId, new BigDecimal("422.64"), 363));

        mockMvc.perform(get("/internal/hub-routes")
                        .param("departureHubId", departureHubId.toString())
                        .param("arrivalHubId", arrivalHubId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubRouteId").value(hubRouteId.toString()))
                .andExpect(jsonPath("$.distanceKm").value(422.64))
                .andExpect(jsonPath("$.durationMinutes").value(363))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.departureHub").doesNotExist());
    }

    @Test
    @DisplayName("I-03에서 경로가 없으면 외부 API와 같은 형식의 404 HUB_ROUTE_NOT_FOUND")
    void notFound() throws Exception {
        given(hubRouteQueryService.getInternalHubRoute(departureHubId, arrivalHubId))
                .willThrow(new BusinessException(HubErrorCode.HUB_ROUTE_NOT_FOUND));

        mockMvc.perform(get("/internal/hub-routes")
                        .param("departureHubId", departureHubId.toString())
                        .param("arrivalHubId", arrivalHubId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("출발 또는 도착 허브 ID가 빠지면 400 VALIDATION_ERROR")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/internal/hub-routes").param("departureHubId", departureHubId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/internal/hub-routes").param("arrivalHubId", arrivalHubId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 허브 ID는 400 VALIDATION_ERROR")
    void invalidUuid() throws Exception {
        mockMvc.perform(get("/internal/hub-routes")
                        .param("departureHubId", "not-a-uuid")
                        .param("arrivalHubId", arrivalHubId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
