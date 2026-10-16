package com.sparta.logistics.hub.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.HubRouteQueryService;
import com.sparta.logistics.hub.application.dto.HubRouteResult;
import com.sparta.logistics.hub.application.dto.HubRouteResult.HubSummary;
import com.sparta.logistics.hub.domain.HubErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HubRouteController.class, properties = "spring.cloud.config.enabled=false")
class HubRouteControllerWebMvcTest {

    private final UUID hubRouteId = UUID.randomUUID();
    private final UUID departureHubId = UUID.randomUUID();
    private final UUID arrivalHubId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubRouteQueryService hubRouteQueryService;

    private HubRouteResult result() {
        return new HubRouteResult(hubRouteId,
                new HubSummary(departureHubId, "서울특별시 센터"),
                new HubSummary(arrivalHubId, "부산광역시 센터"),
                new BigDecimal("422.64"), 363);
    }

    @Test
    @DisplayName("H-04는 ApiResponse로 감싸 두 허브의 ID·이름과 거리·시간을 반환한다")
    void getHubRoute() throws Exception {
        given(hubRouteQueryService.getHubRoute(hubRouteId)).willReturn(result());

        mockMvc.perform(get("/api/v1/hub-routes/{hubRouteId}", hubRouteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.hubRouteId").value(hubRouteId.toString()))
                .andExpect(jsonPath("$.data.departureHub.hubId").value(departureHubId.toString()))
                .andExpect(jsonPath("$.data.departureHub.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.arrivalHub.name").value("부산광역시 센터"))
                .andExpect(jsonPath("$.data.distanceKm").value(422.64))
                .andExpect(jsonPath("$.data.durationMinutes").value(363));
    }

    @Test
    @DisplayName("H-04에서 경로가 없으면 404 HUB_ROUTE_NOT_FOUND")
    void getHubRouteNotFound() throws Exception {
        given(hubRouteQueryService.getHubRoute(hubRouteId)).willThrow(new BusinessException(HubErrorCode.HUB_ROUTE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/hub-routes/{hubRouteId}", hubRouteId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/hub-routes/" + hubRouteId));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 hubRouteId는 400 VALIDATION_ERROR")
    void invalidPathUuid() throws Exception {
        mockMvc.perform(get("/api/v1/hub-routes/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("H-03은 출발·도착 허브 ID를 서비스에 넘기고 PageResponse를 담는다")
    void search() throws Exception {
        given(hubRouteQueryService.search(eq(departureHubId), eq(arrivalHubId), any()))
                .willReturn(new PageImpl<>(List.of(result()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/hub-routes")
                        .param("departureHubId", departureHubId.toString())
                        .param("arrivalHubId", arrivalHubId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].hubRouteId").value(hubRouteId.toString()))
                .andExpect(jsonPath("$.data.content[0].arrivalHub.name").value("부산광역시 센터"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("H-03은 필터 없이도 호출할 수 있고 허용하지 않는 size는 10으로 보정한다")
    void searchWithoutFilter() throws Exception {
        given(hubRouteQueryService.search(eq(null), eq(null), any()))
                .willReturn(new PageImpl<>(List.of(result()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/hub-routes").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 departureHubId는 400 VALIDATION_ERROR")
    void invalidQueryUuid() throws Exception {
        mockMvc.perform(get("/api/v1/hub-routes").param("departureHubId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
