package com.sparta.logistics.order.application.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * I-20 배정 결과 알림 접수(delivery 결정 로그 29). 요청 스레드에서는 넘기기만 하고 바로 돌아간다.
 * Note: delivery는 이 응답을 기다리는 동안 알림 스레드를 쥐고 있다. Slack·company 호출은 AssignmentNotificationHandler가
 * 알림 실행기에서 한다. 실행기가 가득 차도 delivery는 다시 보내지 않으므로 기록만 남긴다(ADR 0016 7번).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentNotificationService {

    private final AssignmentNotificationSender sender;

    public void accept(AssignmentNotification notification) {
        try {
            sender.send(notification);
        } catch (RuntimeException exception) {
            log.error("배정 결과 알림 제출 실패: orderId={}, type={}, reason={}",
                    notification.orderId(), notification.type(), exception.getClass().getSimpleName());
        }
    }
}
