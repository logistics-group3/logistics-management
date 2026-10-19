package com.sparta.logistics.delivery.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.config.HeaderAuditorAware;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.UpdateRecipientCommand;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배송 변경(D-03~D-05, I-11~I-13). 생성(I-09)은 DeliveryCreationService. */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryManagerReader deliveryManagerReader;
    /** I-13 삭제자. 내부 호출은 order가 전달한 X-Username, 헤더가 없으면(스케줄러 보상 등) SYSTEM이다. */
    private final AuditorAware<String> auditorAware;

    /** D-03 배송지·수령인 수정. 현재 배송담당자인 마지막 구간 담당자 또는 MASTER, 업체 배송 출발 후면 INVALID_STATUS_TRANSITION */
    @Transactional
    public DeliveryResponse updateRecipient(UserContext user, UUID deliveryId, UpdateRecipientCommand command) {
        Delivery delivery = getDelivery(deliveryId);
        validateAssignedManager(user, delivery.getLastMileRoute().getManagerId());
        delivery.updateRecipient(command.deliveryAddress(), command.recipientName());
        return DeliveryResponse.from(delivery);
    }

    /**
     * D-04 구간 담당자 재배정(MASTER). 대상이 없거나 배송담당자가 아니면 DELIVERY_MANAGER_NOT_FOUND,
     * 소속 허브가 구간 담당 허브가 아니면 MANAGER_HUB_MISMATCH, 도착·취소된 구간이면 INVALID_STATUS_TRANSITION
     */
    @Transactional
    public DeliveryResponse reassignManager(UUID deliveryId, UUID routeId, UUID managerId) {
        Delivery delivery = getDelivery(deliveryId);
        delivery.getRoute(routeId);
        DeliveryManager manager = deliveryManagerReader.findById(managerId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND));
        delivery.reassignRouteManager(routeId, manager);
        return DeliveryResponse.from(delivery);
    }

    /** D-05 구간 상태 변경. 현재 배송담당자인 해당 구간 담당자 또는 MASTER, 순번·전이 위반이면 INVALID_STATUS_TRANSITION */
    @Transactional
    public DeliveryResponse changeRouteStatus(UserContext user, UUID deliveryId, UUID routeId, ChangeRouteStatusCommand command) {
        Delivery delivery = getDelivery(deliveryId);
        DeliveryRoute route = delivery.getRoute(routeId);
        validateAssignedManager(user, route.getManagerId());
        delivery.changeRouteStatus(routeId, command.status(), command.actual());
        return DeliveryResponse.from(delivery);
    }

    /** I-11 배송 취소. PICKUP_WAITING에서만, 이미 CANCELED면 그대로 200 */
    @Transactional
    public DeliveryStatusResult cancel(UUID deliveryId) {
        Delivery delivery = getDelivery(deliveryId);
        delivery.cancel();
        return DeliveryStatusResult.from(delivery);
    }

    /** I-12 취소 원복(보상). 취소 상태가 아니면 no-op */
    @Transactional
    public DeliveryStatusResult revertCancel(UUID deliveryId) {
        Delivery delivery = getDelivery(deliveryId);
        delivery.revertCancel();
        return DeliveryStatusResult.from(delivery);
    }

    /** I-13 배송 삭제(주문 삭제·생성 보상). 배송과 구간을 논리 삭제한다. 없거나 이미 삭제됐으면 아무것도 하지 않는다(멱등) */
    @Transactional
    public void deleteByOrderId(UUID orderId) {
        String username = auditorAware.getCurrentAuditor().orElse(HeaderAuditorAware.SYSTEM);
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> delivery.softDelete(username));
    }

    private void validateAssignedManager(UserContext user, UUID managerId) {
        // Note: 지정 해제 후에도 구간의 managerId는 남는다. 현재 자격과 배정 관계를 함께 검사한다.
        if (!user.isMaster() && (!user.deliveryManager() || !user.isSelf(managerId))) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private Delivery getDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }
}
