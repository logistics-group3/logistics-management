package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /** U-03 내 정보 조회, I-01 내부 사용자 조회. 논리 삭제된 사용자는 404. */
    public UserResult getUser(UUID userId) {
        return UserResult.from(findUser(userId));
    }

    /** U-08 사용자 비활성화 (논리 삭제). 권한 검사는 컨트롤러의 @MasterOnly. */
    @Transactional
    public void deactivate(UUID userId, UUID deletedBy) {
        findUser(userId).softDelete(deletedBy);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
