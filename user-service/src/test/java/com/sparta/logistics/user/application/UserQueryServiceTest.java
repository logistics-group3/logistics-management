package com.sparta.logistics.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.user.UserFixture;
import com.sparta.logistics.user.application.dto.DeliveryManagerResult;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserQueryService userQueryService;

    private final User hong = UserFixture.user("hong01", "hashed");

    @Test
    @DisplayName("내 정보를 조회하면 헤더의 userId로 찾은 사용자 정보를 돌려준다")
    void getMyInfo() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userQueryService.getMyInfo(contextOf(hong.getId(), Role.USER));

        assertThat(result.userId()).isEqualTo(hong.getId());
        assertThat(result.username()).isEqualTo("hong01");
        assertThat(result.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("내 정보 조회 중 사용자가 탈퇴해 없으면 USER_NOT_FOUND")
    void getMyInfoNotFound() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.getMyInfo(contextOf(userId, Role.USER)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("USER는 자기 자신을 단건 조회할 수 있다")
    void getUserSelf() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userQueryService.getUser(contextOf(hong.getId(), Role.USER), hong.getId());

        assertThat(result.userId()).isEqualTo(hong.getId());
    }

    @Test
    @DisplayName("MASTER는 다른 사용자를 단건 조회할 수 있다")
    void getUserByMaster() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userQueryService.getUser(contextOf(UUID.randomUUID(), Role.MASTER), hong.getId());

        assertThat(result.userId()).isEqualTo(hong.getId());
    }

    @Test
    @DisplayName("USER가 다른 사용자를 조회하면 존재 여부와 관계없이 조회 전에 FORBIDDEN")
    void getUserOtherByUser() {
        assertThatThrownBy(() -> userQueryService.getUser(contextOf(UUID.randomUUID(), Role.USER), hong.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("MASTER가 없는 사용자를 조회하면 USER_NOT_FOUND")
    void getUserNotFound() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.getUser(contextOf(UUID.randomUUID(), Role.MASTER), userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("허브 배송담당자 목록은 userId와 slackId만 담아 돌려준다")
    void getDeliveryManagers() {
        UUID hubId = UUID.randomUUID();
        given(userRepository.findAllByHubIdAndDeliveryManagerTrue(hubId)).willReturn(List.of(hong));

        List<DeliveryManagerResult> result = userQueryService.getDeliveryManagers(hubId);

        assertThat(result).containsExactly(new DeliveryManagerResult(hong.getId(), "U01ABCDE2FG"));
    }

    @Test
    @DisplayName("허브에 배송담당자가 없으면 예외가 아니라 빈 목록이다")
    void getDeliveryManagersEmpty() {
        UUID hubId = UUID.randomUUID();
        given(userRepository.findAllByHubIdAndDeliveryManagerTrue(hubId)).willReturn(List.of());

        assertThat(userQueryService.getDeliveryManagers(hubId)).isEmpty();
    }

    private UserContext contextOf(UUID userId, Role role) {
        return new UserContext(userId, "someone", role, false);
    }
}
