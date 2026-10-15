package com.sparta.logistics.user.presentation.sample;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 인프라 검증용 샘플 API(완료 기준 3, 4). 서비스 기능을 구현하기 시작할 때 이 패키지째 지운다.
 */
@RestController
@RequestMapping("/api/v1/users/sample")
public class AuthSampleController {

    @GetMapping("/me")
    public ApiResponse<UserContext> me(@CurrentUser UserContext user) {
        return ApiResponse.ok(user);
    }

    @RequireRole(Role.MASTER)
    @GetMapping("/admin")
    public ApiResponse<String> admin(@CurrentUser UserContext user) {
        return ApiResponse.ok("MASTER 전용 API 접근 성공: " + user.username());
    }
}
