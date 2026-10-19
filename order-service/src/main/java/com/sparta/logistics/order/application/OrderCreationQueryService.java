package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.order.application.dto.OrderCreationProgress;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.domain.OrderCreation;
import com.sparta.logistics.order.domain.OrderCreationStatus;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderCreationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O-07 주문 생성 진행 조회. 비동기로 접수한 생성의 처리 상태와 결과를 보여 준다(이슈 #16). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCreationQueryService {

    static final String RECOVERY_MESSAGE = "주문 결과의 확인이 필요합니다. 같은 요청을 다시 보내지 말고 주문 목록에서 확인하세요.";

    private final OrderCreationRepository creationRepository;

    /** 없거나 삭제된 기록은 404, 다른 사용자의 기록은 403이다. 주문 응답은 접수 시점 최초 스냅샷이다. */
    public OrderCreationProgress getProgress(UserContext user, UUID orderId) {
        OrderCreation creation = creationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_CREATION_NOT_FOUND));
        user.validateSelfOrMaster(creation.getOrdererId());
        OrderCreationStatus status = creation.getStatus();
        return switch (status) {
            case COMPLETED -> new OrderCreationProgress(orderId, status, null, null, OrderResult.from(creation.getResponseSnapshot()));
            case ABORTED -> new OrderCreationProgress(orderId, status, creation.getFailureCode(), creation.getFailureMessage(), null);
            case RECOVERY_REQUIRED -> new OrderCreationProgress(orderId, status, creation.getFailureReason().name(), RECOVERY_MESSAGE, null);
            case PROCESSING -> new OrderCreationProgress(orderId, status, null, null, null);
        };
    }
}
