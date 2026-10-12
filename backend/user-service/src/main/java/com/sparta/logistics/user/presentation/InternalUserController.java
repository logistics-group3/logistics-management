package com.sparta.logistics.user.presentation;

import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.presentation.dto.InternalUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용 (Gateway가 /internal/** 외부 접근 차단). 내부 API는 래퍼 없이 DTO를 그대로 반환 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal - User", description = "서비스 간 Feign 전용")
public class InternalUserController {

    private final UserQueryService userQueryService;

    @Operation(summary = "I-01 사용자 조회", description = "호출자: gateway(권한 재확인), company, delivery. 탈퇴 사용자는 404")
    @GetMapping("/{userId}")
    public InternalUserResponse getUser(@PathVariable UUID userId) {
        return InternalUserResponse.from(userQueryService.getUser(userId));
    }
}
