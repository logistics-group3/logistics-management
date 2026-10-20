package com.sparta.logistics.order.application.notification;

import com.sparta.logistics.order.application.CompanyReader;
import com.sparta.logistics.order.application.OrderNotificationSender;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 배정 결과 알림 처리(delivery 결정 로그 29). 알림 실행기 스레드에서 실행한다. Slack은 order만 보낸다(ADR 0016).
 * Note: delivery는 주문 확정(I-19)을 받은 뒤에만 배정하므로 알림이 올 때 주문은 커밋돼 있다. 그래도 그사이 취소·삭제됐을 수 있어
 * 주문 상태를 다시 읽는다. 읽은 직후 취소되는 짧은 틈은 막지 않는다(ADR 0016 4번).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentNotificationHandler {

    /** 업체명을 읽지 못했을 때 메시지에 넣는 값. 업체명 때문에 DM을 빠뜨리지 않는다. */
    static final String UNKNOWN_COMPANY = "(업체명 확인 불가)";

    private final OrderRepository orderRepository;
    private final CompanyReader companyReader;
    private final DeliveryManagerDmService deliveryManagerDmService;
    private final OrderNotificationSender orderNotificationSender;

    public void handle(AssignmentNotification notification) {
        Order order = orderRepository.findById(notification.orderId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.ORDERED || !notification.deliveryId().equals(order.getDeliveryId())) {
            log.info("배정 결과 알림 생략: orderId={}, type={}, reason=ORDER_NOT_ACTIVE", notification.orderId(), notification.type());
            return;
        }
        switch (notification.type()) {
            case ASSIGNED -> deliveryManagerDmService.notifyManagers(
                    OrderCreatedEvent.from(order, companyName(order.getSupplierCompanyId()), companyName(order.getReceiverCompanyId())));
            case MANUAL_REQUIRED -> orderNotificationSender.sendManualAssignmentRequired(order.getId(), order.getDeliveryId());
        }
    }

    /** Note: 주문은 업체명을 저장하지 않는다(생성 때 이벤트로만 넘겼다). 배정 시점에 다시 읽고, 실패하면 이름 없이 보낸다. */
    private String companyName(UUID companyId) {
        try {
            return companyReader.getCompany(companyId).name();
        } catch (RuntimeException exception) {
            log.warn("배정 알림 업체명 조회 실패: companyId={}, reason={}", companyId, exception.getClass().getSimpleName());
            return UNKNOWN_COMPANY;
        }
    }
}
