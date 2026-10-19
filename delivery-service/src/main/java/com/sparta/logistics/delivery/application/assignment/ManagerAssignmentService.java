package com.sparta.logistics.delivery.application.assignment;

import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.ManagerAssigner;
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
 * 허브별 구간 담당자 배치 배정(SA 6.3, 결정 로그 27). 배송 생성(I-09)은 구간을 담당자 없이 저장하고, 이 서비스가 나중에 채운다.
 *
 * Note: 한 허브의 배정은 한 번에 하나만 돈다. 허브 잠금을 기다리지 않고 시도해서(try-lock) 못 잡으면 다른 작업이 처리 중이므로
 * 바로 끝낸다. 잡으면 그 허브의 대기 구간을 모아 담당 건수를 한 번 세고, 메모리에서 차례로 고른 뒤 한 트랜잭션에 저장한다.
 * 그래서 동기 배정처럼 요청마다 잠금을 기다리며 커넥션을 쥐는 일이 없다. 배송담당자 후보 조회(I-14)는 트랜잭션 밖에서 먼저 한다.
 * 배송 행은 다른 변경과 같은 규칙으로 잠그되(결정 로그 21), 다른 요청이 잡은 배송은 기다리지 않고 다음 실행으로 넘긴다.
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

    /** 이 허브의 배정 대기 구간을 최대 BATCH_SIZE건의 배송만큼 배정한다. 몇 번 불려도 이미 배정된 구간은 그대로다. */
    public AssignmentResult assignPending(UUID hubId) {
        List<UUID> candidates = deliveryManagerReader.findIdsByHub(hubId);
        if (candidates.isEmpty()) {
            // 결정 로그 27: 후보가 없으면 대기로 둔다. 주기 작업이 다시 시도하고, MASTER가 D-04로 직접 배정할 수 있다.
            log.warn("배정 보류: hubId={}, reason=NO_DELIVERY_MANAGER", hubId);
            return AssignmentResult.noCandidates();
        }
        return transactionOperations.execute(status -> assignInTransaction(hubId, candidates));
    }

    private AssignmentResult assignInTransaction(UUID hubId, List<UUID> candidates) {
        if (!deliveryRepository.tryLockHubAssignment(hubId)) {
            return AssignmentResult.busy();
        }
        List<UUID> deliveryIds = deliveryRepository.lockDeliveriesAwaitingManager(hubId, BATCH_SIZE);
        if (deliveryIds.isEmpty()) {
            return AssignmentResult.assigned(0, List.of(), false);
        }
        List<Delivery> deliveries = deliveryRepository.findAllWithRoutesByIdIn(deliveryIds).stream()
                .sorted(Comparator.comparing(Delivery::getCreatedAt))
                .toList();
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

    private Map<UUID, Long> countActiveRoutes(List<UUID> candidates) {
        return deliveryQueryRepository.countActiveRoutes(candidates).stream()
                .collect(Collectors.toMap(ActiveRouteCount::managerId, ActiveRouteCount::count));
    }
}
