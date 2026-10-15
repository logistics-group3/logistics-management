package com.sparta.logistics.user.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.InternalUserResult;
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
public class UserQueryService {

    private final UserRepository userRepository;

    /** U-03 내 정보 조회. Gateway가 탈퇴자를 먼저 막지만, 권한 재확인 캐시(60초) 사이에 탈퇴했으면 404다. */
    public UserResult getMyInfo(UserContext user) {
        return UserResult.from(findUser(user.userId()));
    }

    /**
     * U-04 사용자 단건 조회. 본인 또는 MASTER만 조회할 수 있다.
     * Note: 권한 검사를 조회보다 먼저 한다. 조회를 먼저 하면 USER가 남의 ID를 넣었을 때 있으면 403, 없으면 404가 나와
     * 응답 코드만으로 다른 사용자 ID의 존재 여부를 알아낼 수 있다.
     */
    public UserResult getUser(UserContext user, UUID userId) {
        user.validateSelfOrMaster(userId);
        return UserResult.from(findUser(userId));
    }

    /** 탈퇴 사용자는 @SQLRestriction으로 조회되지 않아 404가 된다(SA I-01). */
    public InternalUserResult getInternalUser(UUID userId) {
        return InternalUserResult.from(findUser(userId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
