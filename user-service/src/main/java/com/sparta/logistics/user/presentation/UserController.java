package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.presentation.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "사용자 조회와 관리(U-03~U-08)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserQueryService userQueryService;

    /** Note: "/me"와 "/{userId}"가 둘 다 맞아 보여도 Spring MVC는 변수 없는 경로를 더 구체적인 것으로 보고 먼저 고른다. */
    @Operation(summary = "U-03 내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@CurrentUser UserContext user) {
        return ApiResponse.ok(UserResponse.from(userQueryService.getMyInfo(user)));
    }

    @Operation(summary = "U-04 사용자 단건 조회")
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUser(@CurrentUser UserContext user, @PathVariable UUID userId) {
        return ApiResponse.ok(UserResponse.from(userQueryService.getUser(user, userId)));
    }
}
