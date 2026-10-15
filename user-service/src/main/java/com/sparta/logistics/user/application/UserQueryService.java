package com.sparta.logistics.user.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.application.dto.DeliveryManagerResult;
import com.sparta.logistics.user.application.dto.InternalUserResult;
import com.sparta.logistics.user.application.dto.UserResult;
import com.sparta.logistics.user.domain.User;
import com.sparta.logistics.user.domain.UserErrorCode;
import com.sparta.logistics.user.domain.UserRepository;
import com.sparta.logistics.user.domain.UserSearchCondition;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** U-05 사용자 검색(MASTER). 정렬 허용 필드는 createdAt, username이다. */
    public Page<UserResult> search(UserSearchCondition condition, Pageable pageable) {
        return userRepository.search(condition, pageable).map(UserResult::from);
    }

    /** 탈퇴 사용자는 @SQLRestriction으로 조회되지 않아 404가 된다(SA I-01). */
    public InternalUserResult getInternalUser(UUID userId) {
        return InternalUserResult.from(findUser(userId));
    }

    /**
     * I-14 허브 소속 배송담당자 목록. 없으면 빈 목록이다(SA 6.3).
     * Note: 담당자가 없을 때 404로 실패시킬지는 호출하는 쪽의 업무 판단이다. user는 사실(빈 목록)만 돌려주고,
     * delivery가 그 결과로 DELIVERY_MANAGER_NOT_FOUND를 낼지 정한다.
     */
    public List<DeliveryManagerResult> getDeliveryManagers(UUID hubId) {
        return userRepository.findAllByHubIdAndDeliveryManagerTrue(hubId).stream()
                .map(DeliveryManagerResult::from)
                .toList();
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
