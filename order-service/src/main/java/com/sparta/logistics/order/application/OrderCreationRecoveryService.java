package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.page.SortAllowList;
import com.sparta.logistics.order.application.dto.DeliveryCreationState;
import com.sparta.logistics.order.application.dto.OrderCreationRecoveryResult;
import com.sparta.logistics.order.application.dto.OrderRecoveryCandidateResult;
import com.sparta.logistics.order.application.dto.RecoverOrderCreationCommand;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCreationRecoveryService {
    private final OrderCreationRecoveryWriter writer;
    private final DeliveryGateway delivery;
    private final OrderCreationRepository creations;

    /** O-09 운영자가 원래 실행 중단을 확인한 생성 시도만 정리한다. */
    @Transactional(propagation = Propagation.NEVER)
    public OrderCreationRecoveryResult recover(UserContext user, UUID orderId, RecoverOrderCreationCommand command) {
        requireMaster(user);
        if (!command.originalExecutionStopped() || command.reason() == null || command.reason().isBlank()
                || command.reason().length() > 500
                || (command.expectedStatus() != OrderCreationStatus.PROCESSING && command.expectedStatus() != OrderCreationStatus.RECOVERY_REQUIRED)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        OrderCreationRecoveryWriter.Claim claim;
        try {
            claim = writer.claim(orderId, user.userId(), command);
        } catch (DataIntegrityViolationException conflict) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION, "복구 요청 ID가 다른 작업에서 사용 중입니다.");
        }
        if (!claim.execute()) return writer.get(claim.recoveryId());
        try {
            DeliveryCreationState observed = delivery.getCreationState(orderId);
            writer.record(claim.recoveryId(), claim.executionId(), "OBSERVED", observed.toString());
            if ("ACTIVE".equals(observed.state()) && !"PICKUP_WAITING".equals(observed.status())) {
                writer.defer(claim.recoveryId(), claim.executionId(), "진행·완료·취소된 배송은 자동 정리하지 않습니다.");
            } else {
                if (!"CLOSED".equals(observed.state())) {
                    writer.record(claim.recoveryId(), claim.executionId(), "CLOSE_REQUESTED", observed.toString());
                    try {
                        delivery.closeCreation(orderId, observed.deliveryId());
                    } catch (RuntimeException unconfirmed) {
                        // Note: 응답 유실도 반영 실패를 뜻하지 않는다. 이어지는 I-17의 CLOSED만 종료 근거로 삼는다.
                        writer.record(claim.recoveryId(), claim.executionId(), "CLOSE_UNCONFIRMED", "조건부 종료 응답을 확인하지 못했다.");
                    }
                }
                DeliveryCreationState verified = delivery.getCreationState(orderId);
                writer.record(claim.recoveryId(), claim.executionId(), "VERIFIED", verified.toString());
                if ("CLOSED".equals(verified.state())) {
                    writer.finish(orderId, claim.recoveryId(), claim.executionId());
                } else {
                    writer.defer(claim.recoveryId(), claim.executionId(), "배송 생성 종료를 확인하지 못했습니다.");
                }
            }
        } catch (RuntimeException failure) {
            String detail = failure instanceof BusinessException business ? business.getErrorCode().code() : "확인·저장 실패";
            writer.defer(claim.recoveryId(), claim.executionId(), "복구를 보류했습니다: " + detail);
        }
        return writer.get(claim.recoveryId());
    }

    /** O-08 경과 시간은 조사 대상을 고르는 근거일 뿐 실행 종료의 증거가 아니다. */
    @Transactional(readOnly = true)
    public Page<OrderRecoveryCandidateResult> candidates(UserContext user, Pageable pageable) {
        requireMaster(user);
        SortAllowList.check(pageable, Set.of("createdAt", "updatedAt"));
        return creations.findRecoveryCandidates(pageable).map(OrderRecoveryCandidateResult::from);
    }

    public OrderCreationRecoveryResult get(UserContext user, UUID recoveryId) {
        requireMaster(user);
        return writer.get(recoveryId);
    }

    private void requireMaster(UserContext user) {
        if (!user.isMaster()) throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}
