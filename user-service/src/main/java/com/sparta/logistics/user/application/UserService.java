package com.sparta.logistics.user.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.user.application.dto.ChangeRoleCommand;
import com.sparta.logistics.user.application.dto.HubInfo;
import com.sparta.logistics.user.application.dto.UpdateUserCommand;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 변경(U-06~U-08). MASTER 권한은 컨트롤러 @RequireRole이 확인한다. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** U-06 사용자 정보 수정. 배송담당자의 소속은 바꿀 수 없다(user 결정 0001). */
    @Transactional
    public UserResult update(UUID userId, UpdateUserCommand command) {
        User user = findUser(userId);
        user.updateProfile(command.name(), command.slackId(), command.affiliationType(), command.affiliationName());
        return UserResult.from(user);
    }

    /**
     * U-07의 DB 변경 부분. 허브 조회는 UserRoleService가 트랜잭션 밖에서 끝내고 결과(hub)만 넘긴다.
     * hub가 null이면 hubId를 보내지 않은 요청이다.
     */
    @Transactional
    public UserResult applyRoleChange(UUID userId, ChangeRoleCommand command, HubInfo hub) {
        User user = findUser(userId);
        if (command.role() != null) {
            user.changeRole(command.role());
        }
        if (Boolean.FALSE.equals(command.isDeliveryManager())) {
            user.releaseDeliveryManager();
        } else if (hub != null) {
            if (command.isDeliveryManager() == null && !user.isDeliveryManager()) {
                // 배송담당자가 아닌 사용자에게 hubId만 보낸 요청은 지정인지 실수인지 모호하다(user 결정 0001).
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "배송담당자가 아닌 사용자의 소속 허브를 바꾸려면 isDeliveryManager를 true로 함께 보내야 합니다.");
            }
            user.assignDeliveryManager(hub.hubId(), hub.name());
        }
        return UserResult.from(user);
    }

    /** U-08 사용자 비활성화(논리 삭제). 자기 자신은 삭제할 수 없다(마지막 MASTER 보호, SA U-08). */
    @Transactional
    public void delete(UserContext master, UUID userId) {
        if (master.isSelf(userId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "자기 자신은 삭제할 수 없습니다.");
        }
        findUser(userId).softDelete(master.username());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
