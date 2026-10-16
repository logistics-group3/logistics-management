package com.sparta.logistics.user.presentation;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.exception.GlobalExceptionHandler;
import com.sparta.logistics.user.application.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(mock(AuthService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("SA 8.5 규칙을 어긴 username과 password는 VALIDATION_ERROR")
    void rejectsInvalidSignup() throws Exception {
        String body = """
                {"username":"Hong","password":"weak","name":"홍길동","slackEmail":"hong@example.com",
                 "affiliationType":"COMPANY","affiliationName":"부산수산도매"}
                """;
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @DisplayName("정의되지 않은 소속 구분 값은 VALIDATION_ERROR")
    void rejectsUnknownEnum() throws Exception {
        String body = """
                {"username":"hong01","password":"Sparta!2026","name":"홍길동","slackEmail":"hong@example.com",
                 "affiliationType":"SCHOOL","affiliationName":"부산수산도매"}
                """;
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
