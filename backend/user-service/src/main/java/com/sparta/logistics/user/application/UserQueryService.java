package com.sparta.logistics.user.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.exception.UserErrorCode;
import com.sparta.logistics.user.domain.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 간 조회 (I-01). 사용자 관리 API(U-03~U-08)는 user-service 담당자가 별도 서비스로 추가한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    /** I-01. 탈퇴(논리 삭제) 사용자는 404 → Gateway가 401 TOKEN_ROLE_MISMATCH로 처리 */
    public UserResult getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResult::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
