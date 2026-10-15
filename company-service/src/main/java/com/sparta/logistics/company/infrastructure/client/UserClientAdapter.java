package com.sparta.logistics.company.infrastructure.client;

import com.sparta.logistics.company.application.UserReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserClientAdapter implements UserReader {

    private final UserClient userClient;

    /** 404는 FeignErrorDecoder가 BusinessException(USER_NOT_FOUND)으로 바꾼다. 응답이 오면 활성 사용자다. */
    @Override
    public void checkExists(UUID userId) {
        userClient.getUser(userId);
    }
}
