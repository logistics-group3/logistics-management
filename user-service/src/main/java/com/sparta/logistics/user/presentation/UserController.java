package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.context.CurrentUser;
import com.sparta.logistics.common.context.MasterOnly;
import com.sparta.logistics.common.context.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.UserService;
import com.sparta.logistics.user.presentation.dto.UserResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공통 인프라 단계에서는 검증용 U-03, U-08만 제공한다. (U-04~U-07은 서비스 담당자 구현) */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** U-03 내 정보 조회 */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@CurrentUser UserContext user) {
        return ApiResponse.ok(UserResponse.from(userService.getUser(user.userId())));
    }

    /** U-08 사용자 비활성화 (논리 삭제) */
    @MasterOnly
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deactivate(@PathVariable UUID userId, @CurrentUser UserContext user) {
        userService.deactivate(userId, user.userId());
        return ApiResponse.ok();
    }
}
