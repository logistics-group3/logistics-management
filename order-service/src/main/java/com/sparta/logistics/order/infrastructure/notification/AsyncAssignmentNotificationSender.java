package com.sparta.logistics.order.infrastructure.notification;

import com.sparta.logistics.order.application.notification.AssignmentNotification;
import com.sparta.logistics.order.application.notification.AssignmentNotificationHandler;
import com.sparta.logistics.order.application.notification.AssignmentNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

/** 배정 결과 알림을 알림 실행기에서 처리한다. DM이 설정되지 않았으면 배정 완료 DM을 건너뛴다. */
@Slf4j
@RequiredArgsConstructor
public class AsyncAssignmentNotificationSender implements AssignmentNotificationSender {

    private final SlackDmProperties dmProperties;
    private final AssignmentNotificationHandler handler;

    @Override
    @Async("orderNotificationExecutor")
    public void send(AssignmentNotification notification) {
        if (notification.type() == AssignmentNotification.Type.ASSIGNED && !dmProperties.configured()) {
            return;
        }
        try {
            handler.handle(notification);
        } catch (RuntimeException exception) {
            // Note: 하위 HTTP 오류에는 비밀값·이메일이 포함될 수 있어 메시지·스택을 기록하지 않는다.
            log.warn("배정 결과 알림 실패: orderId={}, type={}, reason={}",
                    notification.orderId(), notification.type(), exception.getClass().getSimpleName());
        }
    }
}
