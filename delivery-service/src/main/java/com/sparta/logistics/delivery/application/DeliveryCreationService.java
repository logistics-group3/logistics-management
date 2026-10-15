package com.sparta.logistics.delivery.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.domain.ActiveRouteCount;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.ManagerAssigner;
import com.sparta.logistics.delivery.domain.RouteInfo;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * I-09 배송 생성과 구간 담당자 자동 배정(SA 6.3, 7.1).
 *
 * Note: 이 클래스는 일부러 @Transactional을 두지 않는다. 이유는 두 가지다.
 * 1) hub·user 호출(최대 read 20초)을 DB 트랜잭션 밖에서 해 커넥션을 오래 잡지 않는다.
 * 2) 동시에 같은 orderId로 저장하면 order_id 유니크 위반이 나는데, 트랜잭션 안에서 이 예외가 나면 그 트랜잭션은
 *    rollback-only가 되어 기존 배송을 다시 읽을 수 없다. saveAndFlush는 Spring Data가 자체 트랜잭션으로 실행하므로
 *    위반은 그 트랜잭션만 롤백하고, 여기서 새 조회로 먼저 저장된 배송을 돌려줄 수 있다.
 * 배송과 구간은 cascade PERSIST로 saveAndFlush 한 번(한 트랜잭션)에 함께 저장된다(SA 7.5).
 */
@Service
@RequiredArgsConstructor
public class DeliveryCreationService {

    private final DeliveryRepository deliveryRepository;
    private final HubRouteReader hubRouteReader;
    private final DeliveryManagerReader deliveryManagerReader;
    private final RecipientReader recipientReader;

    /** I-09 배송 생성. 같은 orderId 배송이 있으면 그대로 돌려준다(멱등). 구간 담당 허브에 배송담당자가 없으면 저장 전에 404. */
    public CreateDeliveryResult create(CreateDeliveryCommand command) {
        Optional<Delivery> existing = deliveryRepository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            return CreateDeliveryResult.existing(existing.get());
        }

        boolean sameHub = command.departureHubId().equals(command.destinationHubId());
        RouteInfo hubTransferExpected = sameHub
                ? null
                : hubRouteReader.getExpectedRoute(command.departureHubId(), command.destinationHubId());
        ManagerAssigner assigner = prepareAssigner(command.departureHubId(), command.destinationHubId());
        // 수령인 Slack ID가 없어도(요청업체 관리자 탈퇴) 배송은 만든다. 컬럼이 NULL을 허용하고 알림만 빠진다.
        String recipientSlackId = recipientReader.findSlackId(command.receiverManagerId()).orElse(null);

        Delivery delivery = Delivery.create(command.orderId(), command.supplierCompanyId(), command.receiverCompanyId(),
                command.departureHubId(), command.destinationHubId(), command.pickupAddress(), command.deliveryAddress(),
                command.recipientName(), recipientSlackId, hubTransferExpected, assigner);
        try {
            return CreateDeliveryResult.created(deliveryRepository.saveAndFlush(delivery));
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 같은 orderId 요청이 먼저 저장했다. 그 배송을 돌려준다. 다른 제약 위반이면 그대로 올린다.
            return deliveryRepository.findByOrderId(command.orderId())
                    .map(CreateDeliveryResult::existing)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * 구간 담당 허브(공급업체 허브, 요청업체 허브. 같으면 1개)마다 I-14로 후보를 받고, 후보 전원의 담당 건수를 한 번에 센다.
     * 어느 허브든 후보가 없으면 저장 전에 DELIVERY_MANAGER_NOT_FOUND로 끝나 남는 데이터가 없다(SA 7.1 ②).
     */
    private ManagerAssigner prepareAssigner(UUID supplierHubId, UUID receiverHubId) {
        Map<UUID, List<UUID>> candidatesByHub = new LinkedHashMap<>();
        for (UUID hubId : new LinkedHashSet<>(List.of(supplierHubId, receiverHubId))) {
            List<UUID> candidates = deliveryManagerReader.findIdsByHub(hubId);
            if (candidates.isEmpty()) {
                throw new BusinessException(DeliveryErrorCode.DELIVERY_MANAGER_NOT_FOUND);
            }
            candidatesByHub.put(hubId, candidates);
        }
        Set<UUID> allCandidates = candidatesByHub.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<UUID, Long> counts = deliveryRepository.countActiveRoutes(allCandidates).stream()
                .collect(Collectors.toMap(ActiveRouteCount::managerId, ActiveRouteCount::count));
        return new ManagerAssigner(candidatesByHub, counts);
    }
}
