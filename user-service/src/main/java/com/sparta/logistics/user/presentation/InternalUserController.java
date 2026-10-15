package com.sparta.logistics.user.presentation;

import com.sparta.logistics.user.application.UserQueryService;
import com.sparta.logistics.user.application.dto.InternalUserResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 호출 전용(I-01). Gateway에 라우팅이 없어 외부에서 호출할 수 없다.
 * Note: 내부 API는 서비스끼리의 계약이라 DTO를 ApiResponse로 감싸지 않고 그대로 반환한다(ADR 0010). 실패는 외부 API와 같은
 * SA 6.1 에러 형식이라 호출하는 쪽 FeignErrorDecoder가 code를 그대로 읽는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserQueryService userQueryService;

    @GetMapping("/{userId}")
    public InternalUserResult getUser(@PathVariable UUID userId) {
        return userQueryService.getInternalUser(userId);
    }
}
