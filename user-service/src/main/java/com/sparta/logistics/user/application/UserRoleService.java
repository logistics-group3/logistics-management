package com.sparta.logistics.user.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.user.application.dto.ChangeRoleCommand;
import com.sparta.logistics.user.application.dto.HubInfo;
import com.sparta.logistics.user.application.dto.UserResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * U-07 권한·배송담당자 지정.
 * Note: 일부러 @Transactional을 걸지 않는다. 트랜잭션 안에서 hub-service를 부르면 응답을 기다리는 동안(최대 read 20초 × 재시도)
 * DB 커넥션을 붙잡아, 호출이 몰리면 커넥션 풀이 바닥난다. 허브 조회를 먼저 끝내고 DB 변경만 UserService의 짧은 트랜잭션으로 한다.
 * 같은 클래스 안에서 @Transactional 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 시작되지 않으므로 클래스를 나눴다.
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final HubReader hubReader;
    private final UserService userService;

    public UserResult changeRole(UserContext master, UUID userId, ChangeRoleCommand command) {
        // Gateway가 토큰의 role을 DB와 다시 대조하므로 master.role()은 현재 저장된 권한이다. DB 조회 없이 먼저 거른다.
        if (master.isSelf(userId) && command.role() != null && command.role() != master.role()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "자기 자신의 권한은 바꿀 수 없습니다.");
        }
        HubInfo hub = command.hubId() != null ? hubReader.getHub(command.hubId()) : null;
        return userService.applyRoleChange(userId, command, hub);
    }
}
