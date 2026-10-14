package com.sparta.logistics.user.presentation;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.application.dto.InternalUserResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용(I-01). Gateway에 라우팅이 없어 외부에서 호출할 수 없다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserQueryService userQueryService;

    @GetMapping("/{userId}")
    public ApiResponse<InternalUserResult> getUser(@PathVariable UUID userId) {
        return ApiResponse.ok(userQueryService.getInternalUser(userId));
    }
}
