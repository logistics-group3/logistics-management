package com.sparta.logistics.user.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.SimpleErrorCode;
import com.sparta.logistics.user.application.dto.ChangeRoleCommand;
import com.sparta.logistics.user.application.dto.HubInfo;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private HubReader hubReader;
    @Mock
    private UserService userService;
    @InjectMocks
    private UserRoleService userRoleService;

    private final UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, false);
    private final UUID target = UUID.randomUUID();

    @Test
    @DisplayName("hubId를 보내면 허브를 먼저 조회하고 그 결과로 DB를 바꾼다")
    void looksUpHubBeforeChange() {
        UUID hubId = UUID.randomUUID();
        HubInfo hub = new HubInfo(hubId, "서울특별시 센터");
        ChangeRoleCommand command = new ChangeRoleCommand(null, true, hubId);
        given(hubReader.getHub(hubId)).willReturn(hub);

        userRoleService.changeRole(master, target, command);

        verify(userService).applyRoleChange(target, command, hub);
    }

    @Test
    @DisplayName("hubId가 없으면 hub-service를 부르지 않는다")
    void noHubLookupWithoutHubId() {
        ChangeRoleCommand command = new ChangeRoleCommand(Role.MASTER, false, null);

        userRoleService.changeRole(master, target, command);

        verifyNoInteractions(hubReader);
        verify(userService).applyRoleChange(target, command, null);
    }

    @Test
    @DisplayName("MASTER가 자기 권한을 바꾸려 하면 허브 조회와 DB 변경 전에 INVALID_REQUEST")
    void cannotChangeOwnRole() {
        assertThatThrownBy(() -> userRoleService.changeRole(master, master.userId(),
                new ChangeRoleCommand(Role.USER, true, UUID.randomUUID())))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
        verifyNoInteractions(hubReader, userService);
    }

    @Test
    @DisplayName("자기 자신이라도 권한을 그대로 두고 배송담당자 지정만 하는 것은 된다")
    void selfDeliveryManagerAssignmentAllowed() {
        ChangeRoleCommand command = new ChangeRoleCommand(Role.MASTER, false, null);

        userRoleService.changeRole(master, master.userId(), command);

        verify(userService).applyRoleChange(master.userId(), command, null);
    }

    @Test
    @DisplayName("hub-service가 HUB_NOT_FOUND로 응답하면 DB를 바꾸지 않고 그 코드를 그대로 올린다")
    void hubNotFound() {
        UUID hubId = UUID.randomUUID();
        given(hubReader.getHub(hubId)).willThrow(new BusinessException(
                new SimpleErrorCode(HttpStatus.NOT_FOUND, "HUB_NOT_FOUND", "허브를 찾을 수 없습니다.")));

        assertThatThrownBy(() -> userRoleService.changeRole(master, target, new ChangeRoleCommand(null, true, hubId)))
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo("HUB_NOT_FOUND");
        verifyNoInteractions(userService);
    }
}
