package com.sparta.logistics.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태와 코드로 응답하고 errors는 넣지 않는다")
    void businessException() throws Exception {
        mockMvc.perform(get("/sample/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.path").value("/sample/forbidden"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("요청 본문 검증 실패는 VALIDATION_ERROR와 필드별 사유를 응답한다")
    void validationError() throws Exception {
        mockMvc.perform(post("/sample/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"AB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.errors[0].reason").value("4~10자의 소문자와 숫자만 사용할 수 있습니다."));
    }

    @Test
    @DisplayName("JSON 형식이 깨진 본문도 VALIDATION_ERROR로 응답한다")
    void unreadableBody() throws Exception {
        mockMvc.perform(post("/sample/users").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 정보를 숨기고 INTERNAL_SERVER_ERROR로 응답한다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/sample/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 500이 아니라 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/sample/users").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/sample/users"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("컨트롤러 파라미터 검증(@Min) 실패는 500이 아니라 VALIDATION_ERROR와 파라미터별 사유")
    void methodParameterValidation() throws Exception {
        mockMvc.perform(get("/sample/quantity").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].reason").value("1 이상이어야 합니다."));
    }

    @RestController
    static class SampleController {

        @GetMapping("/sample/quantity")
        int quantity(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") int quantity) {
            return quantity;
        }


        @GetMapping("/sample/forbidden")
        void forbidden() {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        @GetMapping("/sample/boom")
        void boom() {
            throw new IllegalStateException("db password=secret");
        }

        @PostMapping("/sample/users")
        void create(@Valid @RequestBody SampleRequest request) {
        }
    }

    record SampleRequest(
            @Pattern(regexp = "^[a-z0-9]{4,10}$", message = "4~10자의 소문자와 숫자만 사용할 수 있습니다.")
            String username
    ) {
    }
}
