package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.RequireRole;
import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.application.UserRoleService;
import com.sparta.logistics.user.application.UserService;
import com.sparta.logistics.user.presentation.dto.ChangeRoleRequest;
import com.sparta.logistics.user.presentation.dto.UpdateUserRequest;
import com.sparta.logistics.user.presentation.dto.UserResponse;
import com.sparta.logistics.user.presentation.dto.UserSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "사용자 조회와 관리(U-03~U-08)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserService userService;
    private final UserRoleService userRoleService;

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

    @Operation(summary = "U-05 사용자 검색")
    @RequireRole(Role.MASTER)
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> search(@ModelAttribute UserSearchRequest request, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(userQueryService.search(request.toCondition(), pageable), UserResponse::from));
    }

    @Operation(summary = "U-06 사용자 정보 수정")
    @RequireRole(Role.MASTER)
    @PatchMapping("/{userId}")
    public ApiResponse<UserResponse> update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(UserResponse.from(userService.update(userId, request.toCommand())));
    }

    @Operation(summary = "U-07 권한·배송담당자 지정")
    @RequireRole(Role.MASTER)
    @PatchMapping("/{userId}/role")
    public ApiResponse<UserResponse> changeRole(@CurrentUser UserContext user, @PathVariable UUID userId,
                                                @Valid @RequestBody ChangeRoleRequest request) {
        return ApiResponse.ok(UserResponse.from(userRoleService.changeRole(user, userId, request.toCommand())));
    }

    @Operation(summary = "U-08 사용자 비활성화(논리 삭제)")
    @RequireRole(Role.MASTER)
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> delete(@CurrentUser UserContext user, @PathVariable UUID userId) {
        userService.delete(user, userId);
        return ApiResponse.ok("사용자가 삭제되었습니다.", null);
    }
}
