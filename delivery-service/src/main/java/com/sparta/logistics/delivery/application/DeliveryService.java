package com.sparta.logistics.delivery.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.config.HeaderAuditorAware;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.application.assignment.AssignedDelivery;
import com.sparta.logistics.delivery.application.assignment.AssignmentNotifier;
import com.sparta.logistics.delivery.application.dto.ChangeRouteStatusCommand;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.UpdateRecipientCommand;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryCreationClosureRepository;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 배송 변경(D-03~D-05, I-11~I-13). 생성(I-09)은 DeliveryCreationService.
 * Note: 변경 명령은 모두 배송 행을 잠그고(findByIdForUpdate) 시작한다. 같은 배송의 요청이 겹치면 나중 요청이 기다렸다가
 * 먼저 끝난 요청의 결과를 읽고 상태 규칙대로 판단하므로, 낡은 상태로 검사하고 덮어쓰는 일이 없다. 다른 배송은 서로 기다리지 않는다.
 * 구간은 배송 애그리거트를 통해서만 바뀌므로 배송 행 하나를 잠그면 구간 변경도 함께 보호된다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryCreationClosureRepository closureRepository;
    private final DeliveryManagerReader deliveryManagerReader;
    /** I-13 삭제자. 내부 호출은 order가 전달한 X-Username, 헤더가 없으면(스케줄러 보상 등) SYSTEM이다. */
    private final AuditorAware<String> auditorAware;
    /** D-04가 원격 조회를 트랜잭션 밖에서 끝낸 뒤 짧은 트랜잭션만 열기 위해 쓴다. */
    private final TransactionOperations transactionOperations;
    private final AssignmentNotifier assignmentNotifier;

    /** D-03 배송지·수령인 수정. 현재 배송담당자인 마지막 구간 담당자 또는 MASTER, 업체 배송 출발 후면 INVALID_STATUS_TRANSITION */
    @Transactional
    public DeliveryResponse updateRecipient(UserContext user, UUID deliveryId, UpdateRecipientCommand command) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        validateAssignedManager(user, delivery.getLastMileRoute().getManagerId());
        delivery.updateRecipient(command.deliveryAddress(), command.recipientName());
        return DeliveryResponse.from(delivery);
    }

    /**
     * D-04 구간 담당자 재배정(MASTER). 대상이 없거나 배송담당자가 아니면 DELIVERY_MANAGER_NOT_FOUND,
     * 소속 허브가 구간 담당 허브가 아니면 MANAGER_HUB_MISMATCH, 도착·취소된 구간이면 INVALID_STATUS_TRANSITION.
     * 이 지정으로 확정된 배송의 빈 구간이 모두 채워지면(수동 지정 포함) 커밋 뒤 order에 배정 완료를 알린다(결정 로그 29).
     * Note: user-service 조회(최대 20초)를 배송 행 잠금 안에 두지 않으려고 조회를 먼저 끝내고 잠금 트랜잭션을 연다.
     * 그래서 없는 구간이어도 담당자 조회 오류가 먼저 나올 수 있다.
     */
    public DeliveryResponse reassignManager(UUID deliveryId, UUID routeId, UUID managerId) {
        DeliveryManager manager = deliveryManagerReader.findById(managerId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND));
        Reassignment reassignment = transactionOperations.execute(status -> {
            Delivery delivery = getDeliveryForUpdate(deliveryId);
            boolean completed = delivery.reassignRouteManager(routeId, manager);
            return new Reassignment(DeliveryResponse.from(delivery),
                    completed ? new AssignedDelivery(delivery.getId(), delivery.getOrderId()) : null);
        });
        if (reassignment.completed() != null) {
            assignmentNotifier.notifyAssigned(List.of(reassignment.completed()));
        }
        return reassignment.response();
    }

    /** D-05 구간 상태 변경. 현재 배송담당자인 해당 구간 담당자 또는 MASTER, 순번·전이 위반이면 INVALID_STATUS_TRANSITION */
    @Transactional
    public DeliveryResponse changeRouteStatus(UserContext user, UUID deliveryId, UUID routeId, ChangeRouteStatusCommand command) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        DeliveryRoute route = delivery.getRoute(routeId);
        validateAssignedManager(user, route.getManagerId());
        delivery.changeRouteStatus(routeId, command.status(), command.actual());
        return DeliveryResponse.from(delivery);
    }

    /** I-11 배송 취소. PICKUP_WAITING에서만, 이미 CANCELED면 그대로 200 */
    @Transactional
    public DeliveryStatusResult cancel(UUID deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        delivery.cancel();
        return DeliveryStatusResult.from(delivery);
    }

    /** I-12 취소 원복(보상). 취소 상태가 아니면 no-op */
    @Transactional
    public DeliveryStatusResult revertCancel(UUID deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        delivery.revertCancel();
        return DeliveryStatusResult.from(delivery);
    }

    /**
     * I-13 배송 삭제(주문 삭제·생성 보상). 배송과 구간을 논리 삭제하고, 대상이 없어도 종료 표시를 남겨 이후 지연된 I-09가
     * 활성 배송을 만들지 못하게 한다(이슈 #14). 없거나 이미 삭제됐어도 204이며 같은 호출을 반복해도 결과가 같다(멱등).
     * Note: I-09 저장과 같은 orderId 잠금을 먼저 잡는다. 그래야 I-09의 "종료 표시 확인 → 저장" 사이에 이 삭제가 끼어들어
     * 표도 배송도 못 보고 지나가는 순서가 생기지 않는다.
     */
    @Transactional
    public void deleteByOrderId(UUID orderId) {
        String username = auditorAware.getCurrentAuditor().orElse(HeaderAuditorAware.SYSTEM);
        closureRepository.lockOrder(orderId);
        closureRepository.close(orderId, username);
        deliveryRepository.findByOrderIdForUpdate(orderId).ifPresent(delivery -> delivery.softDelete(username));
    }

    /** I-18 주문 생성 정리. 출발 전 배송만 정리하고 이후 도착할 I-09도 막는다. */
    @Transactional
    public void closeCreation(UUID orderId, UUID expectedDeliveryId) {
        String username = auditorAware.getCurrentAuditor().orElse(HeaderAuditorAware.SYSTEM);
        // Note: I-09·I-13과 같은 순서로 잠근 뒤 D-05와 같은 배송 행을 잠근다. 조회 후 출발 경쟁을 없앤다.
        closureRepository.lockOrder(orderId);
        var delivery = deliveryRepository.findByOrderIdForUpdate(orderId);
        if (delivery.isPresent() && closureRepository.existsById(orderId)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        delivery.ifPresent(value -> value.closeCreation(expectedDeliveryId, username));
        // close의 flushAutomatically가 위 논리 삭제까지 반영한 뒤 영속성 컨텍스트를 비운다.
        closureRepository.close(orderId, username);
    }

    private void validateAssignedManager(UserContext user, UUID managerId) {
        // Note: 지정 해제 후에도 구간의 managerId는 남는다. 현재 자격과 배정 관계를 함께 검사한다.
        if (!user.isMaster() && (!user.deliveryManager() || !user.isSelf(managerId))) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private record Reassignment(DeliveryResponse response, AssignedDelivery completed) {
    }

    private Delivery getDeliveryForUpdate(UUID deliveryId) {
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }
}
