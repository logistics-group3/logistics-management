package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.presentation.dto.InternalUserResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내부 API — 서비스 간 Feign / Gateway 전용. Gateway 에 라우트가 없어 외부에서 호출할 수 없다. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserQueryService userQueryService;

    /** I-01 내부 사용자 조회 */
    @GetMapping("/{userId}")
    public ApiResponse<InternalUserResponse> getUser(@PathVariable UUID userId) {
        return ApiResponse.ok(InternalUserResponse.from(userQueryService.getUser(userId)));
    }
}
