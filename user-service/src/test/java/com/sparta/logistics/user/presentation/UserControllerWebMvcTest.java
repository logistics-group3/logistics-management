package com.sparta.logistics.user.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.UserErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = UserController.class, properties = "spring.cloud.config.enabled=false")
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("내 정보 조회는 /{userId}가 아닌 /me로 매칭되고, SA 6.4 UserResponse 필드를 ApiResponse로 감싸 응답한다")
    void getMyInfo() throws Exception {
        given(userQueryService.getMyInfo(any())).willReturn(result());

        as(Role.USER, get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.username").value("hong01"))
                .andExpect(jsonPath("$.data.affiliationType").value("COMPANY"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.isDeliveryManager").value(false))
                .andExpect(jsonPath("$.data.hubId").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-13T09:00:00"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("Gateway가 넣는 사용자 헤더가 없으면 UNAUTHORIZED")
    void requiresUserHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("단건 조회는 경로의 userId로 조회한다")
    void getUser() throws Exception {
        given(userQueryService.getUser(any(), eq(userId))).willReturn(result());

        as(Role.USER, get("/api/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("다른 사용자를 조회할 권한이 없으면 FORBIDDEN 에러 형식으로 응답한다")
    void getUserForbidden() throws Exception {
        UUID other = UUID.randomUUID();
        given(userQueryService.getUser(any(), eq(other))).willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        as(Role.USER, get("/api/v1/users/{userId}", other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/" + other));
    }

    @Test
    @DisplayName("없는 사용자면 USER_NOT_FOUND")
    void getUserNotFound() throws Exception {
        given(userQueryService.getUser(any(), eq(userId))).willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        as(Role.USER, get("/api/v1/users/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("userId가 UUID 형식이 아니면 VALIDATION_ERROR")
    void rejectsInvalidUserId() throws Exception {
        as(Role.USER, get("/api/v1/users/{userId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("검색은 쿼리 파라미터를 조건으로 바꾸고 목록 응답 형식(content, page, size, totalElements)으로 감싼다")
    void search() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        given(userQueryService.search(any(), any())).willReturn(new PageImpl<>(List.of(result()), pageable, 1));

        as(Role.MASTER, get("/api/v1/users")
                .param("username", "hong").param("role", "USER").param("isDeliveryManager", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("hong01"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        verify(userQueryService).search(
                argThat(c -> "hong".equals(c.username()) && c.role() == Role.USER && Boolean.FALSE.equals(c.isDeliveryManager())),
                any());
    }

    @Test
    @DisplayName("검색은 MASTER 전용이라 USER가 부르면 서비스를 호출하지 않고 FORBIDDEN")
    void searchRequiresMaster() throws Exception {
        as(Role.USER, get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(userQueryService);
    }

    @Test
    @DisplayName("정의되지 않은 role 값으로 검색하면 VALIDATION_ERROR")
    void searchRejectsUnknownRole() throws Exception {
        as(Role.MASTER, get("/api/v1/users").param("role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions as(Role role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .header(UserHeaders.USER_ID, UUID.randomUUID().toString())
                .header(UserHeaders.USERNAME, "someone")
                .header(UserHeaders.ROLE, role.name())
                .header(UserHeaders.DELIVERY_MANAGER, "false"));
    }

    private UserResult result() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 13, 9, 0);
        return new UserResult(userId, "hong01", "홍길동", "U01ABCDE2FG", AffiliationType.COMPANY, "부산수산도매",
                Role.USER, false, null, at, at);
    }
}
