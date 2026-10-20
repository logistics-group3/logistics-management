package com.sparta.logistics.delivery.application.assignment;

import java.util.Collection;
import java.util.UUID;

/**
 * "이 허브의 배정 대기 구간을 한 번 봐 달라"는 신호. 배정 자체는 ManagerAssignmentService가 한다(결정 로그 27).
 * Note: 깨우는 방법만 이 뒤에 숨긴다. 지금은 같은 프로세스 안의 비동기 실행이고, 메시지 큐로 바꿀 때는 이 구현만 바꾼다.
 * 신호는 잃어도 된다. 대기 구간은 DB에 남고 주기 작업이 다시 깨운다.
 */
public interface AssignmentTrigger {

    void request(Collection<UUID> hubIds);
}
