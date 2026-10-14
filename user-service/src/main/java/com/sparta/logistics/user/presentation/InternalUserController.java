package com.sparta.logistics.user.presentation;

import com.sparta.logistics.user.application.UserService;
import com.sparta.logistics.user.presentation.dto.InternalUserResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 호출 전용 (Gateway에서 외부 접근 차단) */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /** I-01 내부 사용자 조회. 탈퇴(논리 삭제)했거나 없으면 404 USER_NOT_FOUND. */
    @GetMapping("/{userId}")
    public InternalUserResponse getUser(@PathVariable UUID userId) {
        return InternalUserResponse.from(userService.getUser(userId));
    }
}
