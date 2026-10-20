package com.sparta.logistics.delivery.infrastructure.assignment;

import com.sparta.logistics.delivery.application.assignment.DeliveryConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 확정(I-19)을 받지 못한 배송을 주기적으로 확인한다(결정 로그 29). order가 주문을 커밋한 뒤 확정을 보내기 전에 멈췄거나,
 * delivery가 그동안 응답하지 못한 경우를 줍는 안전망이다. 평소에는 주문 커밋 직후 확정이 바로 온다.
 */
@Slf4j
@RequiredArgsConstructor
public class UnconfirmedDeliveryPoller {

    private final DeliveryConfirmationService deliveryConfirmationService;

    @Scheduled(fixedDelayString = "${delivery.confirmation.check.interval-ms:60000}",
            initialDelayString = "${delivery.confirmation.check.interval-ms:60000}")
    public void poll() {
        try {
            deliveryConfirmationService.confirmOverdue();
        } catch (RuntimeException e) {
            log.warn("미확정 배송 확인 실패: reason={}", e.getClass().getSimpleName());
        }
    }
}
