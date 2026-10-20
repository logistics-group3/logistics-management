package com.sparta.logistics.delivery.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.application.assignment.AssignmentTrigger;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.delivery.application.dto.CreateDeliveryResult;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryCreationClosureRepository;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliveryRoute;
import com.sparta.logistics.delivery.domain.RouteInfo;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * I-09 배송 생성(SA 7.1). 구간은 담당자 없이 저장하고, 저장이 커밋되면 구간 담당 허브의 배정 작업을 깨운다(결정 로그 27).
 * 담당자 자동 배정(SA 6.3)은 ManagerAssignmentService가 허브별로 모아서 한다. 그래서 이 요청은 배정 잠금을 기다리지 않는다.
 *
 * Note: 이 클래스는 일부러 @Transactional을 두지 않는다. 이유는 두 가지다.
 * 1) hub·user 호출(최대 read 20초)을 DB 트랜잭션 밖에서 해 커넥션을 오래 잡지 않는다.
 * 2) 저장 시 예외가 나도 여기서 새 조회로 먼저 저장된 배송을 돌려줄 수 있도록, 저장 트랜잭션을 메서드 전체와 분리한다.
 * 저장만 짧은 트랜잭션(TransactionOperations)으로 실행한다. 거기서 같은 orderId의 잠금을 잡고, 이미 저장된 배송이 있으면 그것을,
 * 종료 표시가 있으면 DELIVERY_CREATION_CLOSED를 돌려 지연된 요청이 종료된 시도의 배송을 만들지 못하게 한다(이슈 #14).
 * 배송과 구간은 cascade PERSIST로 saveAndFlush 한 번(한 트랜잭션)에 함께 저장된다(SA 7.5).
 */
@Service
@RequiredArgsConstructor
public class DeliveryCreationService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryCreationClosureRepository closureRepository;
    private final HubRouteReader hubRouteReader;
    private final RecipientReader recipientReader;
    private final TransactionOperations transactionOperations;
    private final AssignmentTrigger assignmentTrigger;

    /**
     * I-09 배송 생성. 같은 orderId 배송이 있으면 그대로 돌려준다(멱등). 배송담당자 후보는 여기서 보지 않는다. 후보가 없어도
     * 배송은 만들어지고 구간이 배정 대기로 남는다(결정 로그 27).
     * 종료된 시도(I-13이 종료 표시를 남긴 orderId)면 원격 호출 전에 409 DELIVERY_CREATION_CLOSED.
     */
    public CreateDeliveryResult create(CreateDeliveryCommand command) {
        Optional<Delivery> existing = deliveryRepository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            return CreateDeliveryResult.existing(existing.get());
        }
        if (closureRepository.existsById(command.orderId())) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_CREATION_CLOSED);
        }

        boolean sameHub = command.departureHubId().equals(command.destinationHubId());
        RouteInfo hubTransferExpected = sameHub
                ? null
                : hubRouteReader.getExpectedRoute(command.departureHubId(), command.destinationHubId());
        // 수령인 Slack ID가 없어도(요청업체 관리자 탈퇴) 배송은 만든다. 컬럼이 NULL을 허용하고 알림만 빠진다.
        String recipientSlackId = recipientReader.findSlackId(command.receiverManagerId()).orElse(null);

        Delivery delivery = Delivery.create(command.orderId(), command.supplierCompanyId(), command.receiverCompanyId(),
                command.departureHubId(), command.destinationHubId(), command.pickupAddress(), command.deliveryAddress(),
                command.recipientName(), recipientSlackId, hubTransferExpected);
        CreateDeliveryResult result;
        try {
            result = transactionOperations.execute(status -> saveUnlessClosed(delivery));
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 같은 orderId 요청이 먼저 저장했다. 그 배송을 돌려준다. 다른 제약 위반이면 그대로 올린다.
            return deliveryRepository.findByOrderId(command.orderId())
                    .map(CreateDeliveryResult::existing)
                    .orElseThrow(() -> e);
        }
        if (result.created()) {
            // 저장 트랜잭션은 이미 커밋됐다. 신호를 잃어도 대기 구간은 DB에 남아 주기 작업이 줍는다.
            assignmentTrigger.request(managingHubs(delivery));
        }
        return result;
    }

    /** 같은 orderId의 I-13과 직렬화한 뒤, 먼저 저장된 배송이 있으면 그것을, 종료 표시가 있으면 거절하고, 아니면 저장한다. */
    private CreateDeliveryResult saveUnlessClosed(Delivery delivery) {
        UUID orderId = delivery.getOrderId();
        closureRepository.lockOrder(orderId);
        Optional<Delivery> existing = deliveryRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return CreateDeliveryResult.existing(existing.get());
        }
        if (closureRepository.existsById(orderId)) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_CREATION_CLOSED);
        }
        return CreateDeliveryResult.created(deliveryRepository.saveAndFlush(delivery));
    }

    private static Set<UUID> managingHubs(Delivery delivery) {
        return delivery.getRoutes().stream().map(DeliveryRoute::getManagingHubId).collect(Collectors.toSet());
    }
}
