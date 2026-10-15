package com.sparta.logistics.hub.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.HubQueryService;
import com.sparta.logistics.hub.application.dto.HubResult;
import com.sparta.logistics.hub.application.dto.InternalHubResult;
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

@WebMvcTest(controllers = {HubController.class, InternalHubController.class}, properties = "spring.cloud.config.enabled=false")
class HubControllerWebMvcTest {

    private final UUID hubId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubQueryService hubQueryService;

    @Test
    @DisplayName("H-02는 ApiResponse로 감싸 허브를 반환한다")
    void getHub() throws Exception {
        given(hubQueryService.getHub(hubId)).willReturn(new HubResult(
                hubId, "서울특별시 센터", "서울특별시 송파구 송파대로 55", new BigDecimal("37.5665000"), new BigDecimal("126.9780000")));

        mockMvc.perform(get("/api/v1/hubs/{hubId}", hubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.data.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.latitude").value(37.5665))
                .andExpect(jsonPath("$.data.longitude").value(126.978));
    }

    @Test
    @DisplayName("H-02에서 허브가 없으면 404 HUB_NOT_FOUND")
    void getHubNotFound() throws Exception {
        given(hubQueryService.getHub(hubId)).willThrow(new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        mockMvc.perform(get("/api/v1/hubs/{hubId}", hubId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/hubs/" + hubId));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 hubId는 400 VALIDATION_ERROR")
    void invalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/hubs/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("H-01은 PageResponse를 담고, 허용하지 않는 size는 10으로 보정해 넘긴다")
    void searchDefaults() throws Exception {
        HubResult hub = new HubResult(hubId, "서울특별시 센터", "서울", new BigDecimal("37.5"), new BigDecimal("126.9"));
        given(hubQueryService.search(eq("서울"), any())).willReturn(new PageImpl<>(List.of(hub), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/hubs").param("keyword", "서울").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("I-02는 ApiResponse로 감싸지 않고 DTO를 그대로 반환한다")
    void internalUnwrapped() throws Exception {
        given(hubQueryService.getInternalHub(hubId)).willReturn(new InternalHubResult(hubId, "서울특별시 센터", "서울"));

        mockMvc.perform(get("/internal/hubs/{hubId}", hubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubId").value(hubId.toString()))
                .andExpect(jsonPath("$.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.address").value("서울"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist());
    }

    @Test
    @DisplayName("I-02에서 허브가 없으면 외부 API와 같은 형식의 404 HUB_NOT_FOUND")
    void internalNotFound() throws Exception {
        given(hubQueryService.getInternalHub(hubId)).willThrow(new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        mockMvc.perform(get("/internal/hubs/{hubId}", hubId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"));
    }
}
