package com.sparta.logistics.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class RoleCheckInterceptorTest {

    private final RoleCheckInterceptor interceptor = new RoleCheckInterceptor();

    @Test
    @DisplayName("@RequireRole(MASTER) 핸들러에 USER가 접근하면 FORBIDDEN")
    void forbiddenForUser() throws Exception {
        // given
        MockHttpServletRequest request = requestWithRole(UserRole.USER);
        HandlerMethod handler = new HandlerMethod(new SampleController(), "masterOnly");

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).hasCode(CommonErrorCode.FORBIDDEN)).isTrue());
    }

    @Test
    @DisplayName("@RequireRole(MASTER) 핸들러에 MASTER는 통과")
    void passForMaster() throws Exception {
        MockHttpServletRequest request = requestWithRole(UserRole.MASTER);
        HandlerMethod handler = new HandlerMethod(new SampleController(), "masterOnly");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
    }

    @Test
    @DisplayName("사용자 헤더가 없으면 UNAUTHORIZED")
    void unauthorizedWithoutHeaders() throws Exception {
        HandlerMethod handler = new HandlerMethod(new SampleController(), "masterOnly");

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).hasCode(CommonErrorCode.UNAUTHORIZED)).isTrue());
    }

    @Test
    @DisplayName("@RequireRole이 없으면 헤더 없이도 통과")
    void passWithoutAnnotation() throws Exception {
        HandlerMethod handler = new HandlerMethod(new SampleController(), "open");

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler)).isTrue();
    }

    private MockHttpServletRequest requestWithRole(UserRole role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserHeaders.USER_ID, UUID.randomUUID().toString());
        request.addHeader(UserHeaders.USERNAME, "hong01");
        request.addHeader(UserHeaders.USER_ROLE, role.name());
        request.addHeader(UserHeaders.DELIVERY_MANAGER, "false");
        return request;
    }

    static class SampleController {

        @RequireRole(UserRole.MASTER)
        public void masterOnly() {
        }

        public void open() {
        }
    }
}
