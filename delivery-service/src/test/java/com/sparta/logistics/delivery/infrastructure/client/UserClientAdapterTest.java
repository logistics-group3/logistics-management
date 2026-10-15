package com.sparta.logistics.delivery.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.SimpleErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.infrastructure.client.dto.UserResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** I-01 결과를 배송 용어로 바꾸는 분기. 404만 "없음"이고 장애는 그대로 올라가야 한다. */
@ExtendWith(MockitoExtension.class)
class UserClientAdapterTest {

    @Mock
    private UserClient userClient;
    @InjectMocks
    private UserClientAdapter adapter;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("user가 404 USER_NOT_FOUND면 배송담당자·Slack ID 모두 빈 값이다")
    void userNotFound() {
        given(userClient.getUser(userId)).willThrow(new BusinessException(
                new SimpleErrorCode(HttpStatus.NOT_FOUND, UserClientAdapter.USER_NOT_FOUND, "사용자를 찾을 수 없습니다.")));

        assertThat(adapter.findById(userId)).isEmpty();
        assertThat(adapter.findSlackId(userId)).isEmpty();
    }

    @Test
    @DisplayName("배송담당자가 아닌 사용자는 재배정 대상이 아니다")
    void notDeliveryManager() {
        given(userClient.getUser(userId)).willReturn(new UserResponse(userId, false, null, "U01"));

        assertThat(adapter.findById(userId)).isEmpty();
    }

    @Test
    @DisplayName("배송담당자면 소속 허브와 함께 돌려준다")
    void deliveryManager() {
        UUID hubId = UUID.randomUUID();
        given(userClient.getUser(userId)).willReturn(new UserResponse(userId, true, hubId, "U01"));

        assertThat(adapter.findById(userId)).contains(new DeliveryManager(userId, hubId));
    }

    @Test
    @DisplayName("user 장애(503)는 없음으로 바꾸지 않고 그대로 올린다")
    void serviceUnavailablePropagates() {
        given(userClient.getUser(userId)).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.findSlackId(userId))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
