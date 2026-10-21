package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderDeletionOutcome;
import com.sparta.logistics.order.application.dto.OrderDeletionTarget;
import com.sparta.logistics.order.domain.DeletionFailureReason;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDeletionService {
    private final OrderDeletionWriter writer;
    private final DeliveryReader delivery;

    /** O-06. MASTER만 취소·배송 완료 주문을 삭제한다. 저장한 허가 근거로 실패한 같은 삭제를 계속한다. */
    @Transactional(propagation = Propagation.NEVER)
    public void delete(UserContext user, UUID orderId) {
        user.validateMaster();
        OrderDeletionTarget target = writer.inspect(orderId);
        boolean delivered = target.deliveryCheckRequired() && delivery.isDelivered(orderId, target.deliveryId());
        try {
            writer.start(target, delivered);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("주문 삭제 시작 기록 실패: orderId={}, cause={}", orderId, exception.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        try {
            writer.delete(user, orderId);
        } catch (RuntimeException failure) {
            OrderDeletionOutcome outcome;
            try {
                outcome = writer.resolve(orderId);
            } catch (RuntimeException verificationFailure) {
                markRecovery(orderId, DeletionFailureReason.ORDER_COMMIT_UNKNOWN);
                throw deletionFailure(failure);
            }
            if (outcome == OrderDeletionOutcome.COMMITTED) {
                log.warn("주문 삭제 커밋 재확인: orderId={}", orderId);
                return;
            }
            DeletionFailureReason reason = outcome == OrderDeletionOutcome.UNKNOWN ? DeletionFailureReason.ORDER_COMMIT_UNKNOWN
                    : failure instanceof BusinessException || failure instanceof FeignException
                            ? DeletionFailureReason.DELIVERY_DELETE_FAILED : DeletionFailureReason.ORDER_WRITE_FAILED;
            markRecovery(orderId, reason);
            throw deletionFailure(failure);
        }
    }

    private void markRecovery(UUID orderId, DeletionFailureReason reason) {
        try {
            writer.requireRecovery(orderId, reason);
        } catch (RuntimeException failure) {
            log.error("주문 삭제 복구 기록 실패: orderId={}, reason={}, cause={}", orderId, reason, failure.getClass().getSimpleName());
        }
    }

    private BusinessException deletionFailure(RuntimeException failure) {
        if (failure instanceof BusinessException business) return business;
        return new BusinessException(failure instanceof FeignException
                ? CommonErrorCode.SERVICE_UNAVAILABLE : CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
}
