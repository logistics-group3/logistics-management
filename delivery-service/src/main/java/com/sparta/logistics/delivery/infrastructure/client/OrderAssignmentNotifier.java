package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.delivery.application.assignment.AssignedDelivery;
import com.sparta.logistics.delivery.application.assignment.AssignmentNotifier;
import com.sparta.logistics.delivery.infrastructure.client.dto.AssignmentNotificationRequest;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배정 결과를 order I-20로 알린다(결정 로그 29).
 * Note: 배정 스레드에서 order를 부르면 order가 느릴 때 배정이 함께 느려진다. 그래서 전용 실행기에 넘기고 바로 돌아간다.
 * 실행기가 가득 차거나 호출이 실패하면 기록만 하고 다시 보내지 않는다(ADR 0016 7번). 배정 결과는 DB에 있으므로
 * MASTER가 배송 목록에서 확인할 수 있다.
 */
@Slf4j
@RequiredArgsConstructor
public class OrderAssignmentNotifier implements AssignmentNotifier {

    private final OrderClient orderClient;
    private final Executor executor;

    @Override
    public void notifyAssigned(Collection<AssignedDelivery> deliveries) {
        deliveries.forEach(delivery -> submit(delivery, AssignmentNotificationRequest.ASSIGNED));
    }

    @Override
    public void notifyManualRequired(Collection<AssignedDelivery> deliveries) {
        deliveries.forEach(delivery -> submit(delivery, AssignmentNotificationRequest.MANUAL_REQUIRED));
    }

    private void submit(AssignedDelivery delivery, String type) {
        try {
            executor.execute(() -> send(delivery, type));
        } catch (RejectedExecutionException e) {
            log.warn("배정 결과 알림 버림: orderId={}, type={}, reason=EXECUTOR_FULL", delivery.orderId(), type);
        }
    }

    private void send(AssignedDelivery delivery, String type) {
        try {
            orderClient.notifyAssignment(delivery.orderId(), new AssignmentNotificationRequest(delivery.deliveryId(), type));
        } catch (RuntimeException e) {
            log.warn("배정 결과 알림 실패: orderId={}, type={}, reason={}", delivery.orderId(), type, e.getClass().getSimpleName());
        }
    }
}
