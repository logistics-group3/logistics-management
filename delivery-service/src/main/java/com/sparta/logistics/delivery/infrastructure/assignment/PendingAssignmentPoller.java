package com.sparta.logistics.delivery.infrastructure.assignment;

import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.assignment.AssignmentTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 배정 대기 구간이 남은 허브를 주기적으로 다시 깨운다(결정 로그 27). 커밋 직후 신호가 버려졌거나, 다른 인스턴스가 처리 중이라
 * 건너뛰었거나, 후보가 없어 대기로 둔 구간을 줍는 안전망이다. 평소에는 커밋 직후 신호가 바로 배정한다.
 */
@Slf4j
@RequiredArgsConstructor
public class PendingAssignmentPoller {

    private final DeliveryQueryRepository deliveryQueryRepository;
    private final AssignmentTrigger assignmentTrigger;

    @Scheduled(fixedDelayString = "${delivery.assignment.poll-interval-ms:30000}",
            initialDelayString = "${delivery.assignment.poll-interval-ms:30000}")
    public void poll() {
        try {
            assignmentTrigger.request(deliveryQueryRepository.findHubsAwaitingManager());
        } catch (RuntimeException e) {
            log.warn("배정 대기 허브 조회 실패: reason={}", e.getClass().getSimpleName());
        }
    }
}
