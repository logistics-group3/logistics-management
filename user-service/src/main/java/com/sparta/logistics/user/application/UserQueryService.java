package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.UserInfoResult;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    /** I-01 내부 사용자 조회. 논리 삭제된 사용자는 404 → Gateway 가 401 TOKEN_ROLE_MISMATCH 로 처리 */
    public UserInfoResult getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserInfoResult::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
