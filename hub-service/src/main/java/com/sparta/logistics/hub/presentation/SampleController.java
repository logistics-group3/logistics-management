package com.sparta.logistics.hub.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.auth.UserRole;
import com.sparta.logistics.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [공통 인프라 검증용 샘플 — hub 도메인 개발 시작할 때 삭제]
 * user-service 가 아닌 서비스에서도 common 모듈(@CurrentUser, @RequireRole, 에러 형식)이 동작하는지 확인한다.
 */
@RestController
@RequestMapping("/api/v1/hubs/sample")
public class SampleController {

    /** 로그인한 사용자 누구나. Gateway 가 넣어준 헤더가 @CurrentUser 로 들어오는지 확인 */
    @GetMapping("/me")
    public ApiResponse<UserContext> me(@CurrentUser UserContext user) {
        return ApiResponse.ok(user);
    }

    /** MASTER 전용. USER 토큰으로 호출하면 403 FORBIDDEN */
    @RequireRole(UserRole.MASTER)
    @GetMapping("/master")
    public ApiResponse<String> masterOnly(@CurrentUser UserContext user) {
        return ApiResponse.ok("MASTER 전용 API 호출 성공: " + user.username());
    }
}
