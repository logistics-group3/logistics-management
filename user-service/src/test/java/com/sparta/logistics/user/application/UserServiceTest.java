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
import com.sparta.logistics.user.application.dto.ChangeRoleCommand;
import com.sparta.logistics.user.application.dto.HubInfo;
import com.sparta.logistics.user.application.dto.UpdateUserCommand;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

    private final User hong = UserFixture.user("hong01", "hashed");
    private final UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, false);
    private final HubInfo seoul = new HubInfo(UUID.randomUUID(), "서울특별시 센터");

    @Test
    @DisplayName("사용자 정보를 수정하면 바뀐 값을 담아 돌려준다")
    void update() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userService.update(hong.getId(), new UpdateUserCommand(null, "U09", AffiliationType.HUB, null, null));

        assertThat(result.slackId()).isEqualTo("U09");
        assertThat(result.affiliationType()).isEqualTo(AffiliationType.HUB);
    }

    @Test
    @DisplayName("없는 사용자를 수정하면 USER_NOT_FOUND")
    void updateNotFound() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(userId, new UpdateUserCommand("김철수", null, null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("배송담당자로 지정하면 허브와 소속이 함께 바뀐다")
    void assignDeliveryManager() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userService.applyRoleChange(hong.getId(), new ChangeRoleCommand(null, true, seoul.hubId()), seoul);

        assertThat(result.isDeliveryManager()).isTrue();
        assertThat(result.hubId()).isEqualTo(seoul.hubId());
        assertThat(result.affiliationName()).isEqualTo("서울특별시 센터");
    }

    @Test
    @DisplayName("이미 배송담당자면 hubId만 보내 소속 허브를 바꿀 수 있다")
    void changeHubOfDeliveryManager() {
        hong.assignDeliveryManager(UUID.randomUUID(), "부산광역시 센터");
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userService.applyRoleChange(hong.getId(), new ChangeRoleCommand(null, null, seoul.hubId()), seoul);

        assertThat(result.hubId()).isEqualTo(seoul.hubId());
        assertThat(result.affiliationName()).isEqualTo("서울특별시 센터");
    }

    @Test
    @DisplayName("배송담당자가 아닌 사용자에게 hubId만 보내면 VALIDATION_ERROR")
    void hubIdOnlyForNonManager() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        assertThatThrownBy(() -> userService.applyRoleChange(hong.getId(), new ChangeRoleCommand(null, null, seoul.hubId()), seoul))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
        assertThat(hong.isDeliveryManager()).isFalse();
    }

    @Test
    @DisplayName("배송담당자를 해제하면 소속 허브가 비고, 권한도 함께 바꿀 수 있다")
    void releaseAndChangeRole() {
        hong.assignDeliveryManager(seoul.hubId(), seoul.name());
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userService.applyRoleChange(hong.getId(), new ChangeRoleCommand(Role.MASTER, false, null), null);

        assertThat(result.isDeliveryManager()).isFalse();
        assertThat(result.hubId()).isNull();
        assertThat(result.role()).isEqualTo(Role.MASTER);
    }

    @Test
    @DisplayName("사용자를 삭제하면 삭제한 MASTER의 username으로 논리 삭제한다")
    void delete() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        userService.delete(master, hong.getId());

        assertThat(hong.isDeleted()).isTrue();
        assertThat(hong.getDeletedBy()).isEqualTo("master");
    }

    @Test
    @DisplayName("자기 자신은 삭제할 수 없고, 조회 전에 거절한다")
    void cannotDeleteSelf() {
        assertThatThrownBy(() -> userService.delete(master, master.userId()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("없는 사용자를 삭제하면 USER_NOT_FOUND")
    void deleteNotFound() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(master, userId))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }
}
