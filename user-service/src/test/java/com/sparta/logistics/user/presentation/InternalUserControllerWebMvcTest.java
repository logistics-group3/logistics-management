package com.sparta.logistics.user.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.application.dto.DeliveryManagerResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InternalUserController.class, properties = "spring.cloud.config.enabled=false")
class InternalUserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    @DisplayName("I-14는 /{userId}가 아닌 /delivery-managers로 매칭되고, 감싸지 않은 배열(userId, slackId)로 응답한다")
    void deliveryManagers() throws Exception {
        UUID hubId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        given(userQueryService.getDeliveryManagers(hubId)).willReturn(List.of(new DeliveryManagerResult(managerId, "U01")));

        mockMvc.perform(get("/internal/users/delivery-managers").param("hubId", hubId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(managerId.toString()))
                .andExpect(jsonPath("$[0].slackId").value("U01"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("I-14에 배송담당자가 없으면 200과 빈 배열이다")
    void noDeliveryManagers() throws Exception {
        UUID hubId = UUID.randomUUID();
        given(userQueryService.getDeliveryManagers(hubId)).willReturn(List.of());

        mockMvc.perform(get("/internal/users/delivery-managers").param("hubId", hubId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("I-14에 hubId가 없으면 VALIDATION_ERROR")
    void requiresHubId() throws Exception {
        mockMvc.perform(get("/internal/users/delivery-managers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
