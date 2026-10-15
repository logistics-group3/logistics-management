package com.sparta.logistics.user.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
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
