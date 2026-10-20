package com.sparta.logistics.delivery.application.assignment;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.application.DeliveryQueryRepository;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 주문 확정(I-19)과 확정 유실 안전망(결정 로그 29).
 *
 * Note: 배송은 I-09에서 미확정으로 저장된다. order는 I-09 응답을 받은 뒤에 주문을 커밋하므로, 그 전에 배정하면 배정 완료 알림이
 * 주문 커밋보다 먼저 도착하거나 생성이 실패할 주문에 담당자가 붙는다. 그래서 order가 커밋한 뒤 확정을 보내고, 그때부터 배정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryConfirmationService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryQueryRepository deliveryQueryRepository;
    private final TransactionOperations transactionOperations;
    private final AssignmentTrigger assignmentTrigger;
    private final OrderCreationReader orderCreationReader;
    private final ConfirmationCheckPolicy confirmationCheckPolicy;

    /**
     * I-19 주문 확정. orderId 배송을 확정하고, 커밋한 뒤 배정 대기 구간의 허브 배정을 깨운다.
     * 이미 확정됐으면 같은 결과를 돌려준다(멱등, order가 다시 보내도 된다). 배송이 없거나 삭제됐으면 DELIVERY_NOT_FOUND.
     * Note: 배정 결과를 기다리지 않는다. order는 확정이 커밋됐다는 응답만 받고, 배정 결과는 나중에 I-20로 따로 받는다.
     */
    public DeliveryStatusResult confirm(UUID orderId) {
        Confirmation confirmation = transactionOperations.execute(status -> {
            Delivery delivery = deliveryRepository.findByOrderIdForUpdate(orderId)
                    .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
            Set<UUID> hubs = delivery.confirm() ? delivery.hubsAwaitingManager() : Set.of();
            return new Confirmation(DeliveryStatusResult.from(delivery), hubs);
        });
        if (!confirmation.hubsToAssign().isEmpty()) {
            // 확정은 커밋됐다. 신호를 잃어도 대기 구간은 DB에 남아 주기 작업이 줍는다.
            assignmentTrigger.request(confirmation.hubsToAssign());
        }
        return confirmation.result();
    }

    /**
     * 확정 유실 안전망. 만든 지 오래됐는데 미확정인 배송을 order에 물어(I-21), 커밋된 주문이면 확정한다.
     * 처리 중이면 다음 확인으로 넘기고, 생성이 실패한 주문의 배송(고아)은 확정하지 않는다. 고아 정리는 order의 복구 흐름이 맡는다.
     * order가 응답하지 않으면 이번 확인을 멈춘다(남은 건도 같은 이유로 실패할 것이라 호출을 줄인다).
     */
    public void confirmOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> orderIds = deliveryQueryRepository.findUnconfirmedOrderIds(now.minus(confirmationCheckPolicy.giveUpAfter()),
                now.minus(confirmationCheckPolicy.after()), confirmationCheckPolicy.batchSize());
        for (UUID orderId : orderIds) {
            OrderCreationCheck check;
            try {
                check = orderCreationReader.getCreationResult(orderId);
            } catch (RuntimeException e) {
                log.warn("미확정 배송 확인 중단: orderId={}, reason={}", orderId, e.getClass().getSimpleName());
                return;
            }
            if (check.confirmable()) {
                confirmQuietly(orderId);
            } else if (!check.inProgress()) {
                log.warn("미확정 배송 확정 안 함: orderId={}, result={}, orderStatus={}", orderId, check.result(), check.orderStatus());
            }
        }
    }

    private void confirmQuietly(UUID orderId) {
        try {
            confirm(orderId);
            log.info("미확정 배송 확정(안전망): orderId={}", orderId);
        } catch (BusinessException e) {
            // 확인하는 사이 보상(I-13)으로 삭제됐다
            log.warn("미확정 배송 확정 실패: orderId={}, reason={}", orderId, e.getErrorCode().code());
        }
    }

    private record Confirmation(DeliveryStatusResult result, Set<UUID> hubsToAssign) {
    }
}
