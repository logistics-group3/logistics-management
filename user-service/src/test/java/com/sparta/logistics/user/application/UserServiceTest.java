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

    @Test
    @DisplayName("사용자 정보를 수정하면 바뀐 값을 담아 돌려준다")
    void update() {
        given(userRepository.findById(hong.getId())).willReturn(Optional.of(hong));

        UserResult result = userService.update(hong.getId(), new UpdateUserCommand(null, "U09", AffiliationType.HUB, null));

        assertThat(result.slackId()).isEqualTo("U09");
        assertThat(result.affiliationType()).isEqualTo(AffiliationType.HUB);
    }

    @Test
    @DisplayName("없는 사용자를 수정하면 USER_NOT_FOUND")
    void updateNotFound() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(userId, new UpdateUserCommand("김철수", null, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
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
