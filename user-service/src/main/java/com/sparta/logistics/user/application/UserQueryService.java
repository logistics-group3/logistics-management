package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.InternalUserResult;
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

    /** 탈퇴 사용자는 @SQLRestriction으로 조회되지 않아 404가 된다(SA I-01). */
    public InternalUserResult getInternalUser(UUID userId) {
        return userRepository.findById(userId)
                .map(InternalUserResult::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
