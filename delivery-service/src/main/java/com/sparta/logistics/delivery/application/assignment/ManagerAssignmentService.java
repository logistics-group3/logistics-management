package com.sparta.logistics.delivery.application.assignment;

import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.ManagerAssigner;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 허브별 구간 담당자 배치 배정(SA 6.3, 결정 로그 27·29). 주문이 확정된 배송(I-19)의 대기 구간을 채운다.
 *
 * Note: 한 허브의 배정은 한 번에 하나만 돈다. 허브 잠금을 기다리지 않고 시도해서(try-lock) 못 잡으면 다른 작업이 처리 중이므로
 * 바로 끝낸다. 잡으면 그 허브의 대기 구간을 모아 담당 건수를 한 번 세고, 메모리에서 차례로 고른 뒤 한 트랜잭션에 저장한다.
 * 그래서 동기 배정처럼 요청마다 잠금을 기다리며 커넥션을 쥐는 일이 없다. 배송담당자 후보 조회(I-14)는 트랜잭션 밖에서 먼저 한다.
 * 배송 행은 다른 변경과 같은 규칙으로 잠그되(결정 로그 21), 다른 요청이 잡은 배송은 기다리지 않고 다음 실행으로 넘긴다.
 *
 * Note: 결과 알림(I-20)은 트랜잭션이 커밋된 뒤에 보낸다. 커밋 전에 보내면 order가 아직 저장되지 않은 배정을 알리게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerAssignmentService {

    /** 한 번에 배정할 최대 배송 수. 트랜잭션이 너무 길어지지 않게 나눈다. 더 남으면 결과의 hasMore로 알린다. */
    static final int BATCH_SIZE = 200;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryQueryRepository deliveryQueryRepository;
    private final DeliveryManagerReader deliveryManagerReader;
    private final TransactionOperations transactionOperations;
    private final AssignmentNotifier assignmentNotifier;
    private final ManualAssignmentPolicy manualAssignmentPolicy;

    /**
     * 이 허브의 배정 대기 구간을 최대 BATCH_SIZE건의 배송만큼 배정한다. 몇 번 불려도 이미 배정된 구간은 그대로다.
     * 후보가 0명이면 기준 시간을 넘은 배송을 수동 지정으로 넘긴다. user 장애로 후보를 못 읽으면 예외가 그대로 올라가고
     * 아무것도 넘기지 않는다(장애가 길어져도 모든 주문이 MASTER에게 몰리지 않게, 결정 로그 29).
     */
    public AssignmentResult assignPending(UUID hubId) {
        List<UUID> candidates = deliveryManagerReader.findIdsByHub(hubId);
        AssignmentResult result = candidates.isEmpty()
                ? transactionOperations.execute(status -> requestManualAssignment(hubId))
                : transactionOperations.execute(status -> assignInTransaction(hubId, candidates));
        notifyOrder(result);
        return result;
    }

    private AssignmentResult assignInTransaction(UUID hubId, List<UUID> candidates) {
        if (!deliveryRepository.tryLockHubAssignment(hubId)) {
            return AssignmentResult.busy();
        }
        List<UUID> deliveryIds = deliveryRepository.lockDeliveriesAwaitingManager(hubId, BATCH_SIZE);
        if (deliveryIds.isEmpty()) {
            return AssignmentResult.assigned(0, List.of(), false);
        }
        List<Delivery> deliveries = lockedDeliveries(deliveryIds);
        ManagerAssigner assigner = new ManagerAssigner(Map.of(hubId, candidates), countActiveRoutes(candidates));
        int assignedRoutes = 0;
        List<AssignedDelivery> completed = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            int assigned = delivery.assignPendingRoutes(hubId, assigner);
            assignedRoutes += assigned;
            if (assigned > 0 && delivery.hasNoRouteAwaitingManager()) {
                completed.add(new AssignedDelivery(delivery.getId(), delivery.getOrderId()));
            }
        }
        return AssignmentResult.assigned(assignedRoutes, completed, deliveryIds.size() == BATCH_SIZE);
    }

    /** 후보 0명. 확정 뒤 기준 시간이 지난 대기 배송을 수동 지정으로 넘긴다. 다른 작업이 허브를 처리 중이면 다음 실행에 맡긴다. */
    private AssignmentResult requestManualAssignment(UUID hubId) {
        if (!deliveryRepository.tryLockHubAssignment(hubId)) {
            return AssignmentResult.noCandidates(List.of());
        }
        LocalDateTime confirmedBefore = LocalDateTime.now().minus(manualAssignmentPolicy.after());
        List<UUID> deliveryIds = deliveryRepository.lockDeliveriesAwaitingManagerConfirmedBefore(hubId, confirmedBefore, BATCH_SIZE);
        if (deliveryIds.isEmpty()) {
            log.warn("배정 보류: hubId={}, reason=NO_DELIVERY_MANAGER", hubId);
            return AssignmentResult.noCandidates(List.of());
        }
        List<AssignedDelivery> manualRequired = lockedDeliveries(deliveryIds).stream()
                .filter(Delivery::requestManualAssignment)
                .map(delivery -> new AssignedDelivery(delivery.getId(), delivery.getOrderId()))
                .toList();
        log.warn("수동 지정 요청: hubId={}, deliveries={}, reason=NO_DELIVERY_MANAGER", hubId, manualRequired.size());
        return AssignmentResult.noCandidates(manualRequired);
    }

    private List<Delivery> lockedDeliveries(List<UUID> deliveryIds) {
        return deliveryRepository.findAllWithRoutesByIdIn(deliveryIds).stream()
                .sorted(Comparator.comparing(Delivery::getCreatedAt))
                .toList();
    }

    private void notifyOrder(AssignmentResult result) {
        if (!result.completed().isEmpty()) {
            assignmentNotifier.notifyAssigned(result.completed());
        }
        if (!result.manualRequired().isEmpty()) {
            assignmentNotifier.notifyManualRequired(result.manualRequired());
        }
    }

    private Map<UUID, Long> countActiveRoutes(List<UUID> candidates) {
        return deliveryQueryRepository.countActiveRoutes(candidates).stream()
                .collect(Collectors.toMap(ActiveRouteCount::managerId, ActiveRouteCount::count));
    }
}
