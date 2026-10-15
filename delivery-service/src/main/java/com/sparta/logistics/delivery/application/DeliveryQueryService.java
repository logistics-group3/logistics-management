package com.sparta.logistics.delivery.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.application.dto.DeliveryResponse;
import com.sparta.logistics.delivery.application.dto.DeliveryRouteResult;
import com.sparta.logistics.delivery.application.dto.DeliverySearchCondition;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 조회(D-01, D-02, I-10). Entity를 읽지 않고 DeliveryQueryRepository가 만든 DTO를 그대로 돌려준다(결정 로그 17).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final DeliveryQueryRepository deliveryQueryRepository;

    /** D-01 배송 검색. 배송담당자는 조회 범위를 조건에 넣어 DB에서 거른다(가져온 뒤 거르면 페이지·count가 틀어진다) */
    public Page<DeliverySummaryResult> search(UserContext user, DeliverySearchCondition condition, Pageable pageable) {
        DeliverySearchCondition scoped = isScopedToOwnRoutes(user) ? condition.limitToManager(user.userId()) : condition;
        return deliveryQueryRepository.search(scoped, pageable);
    }

    /** D-02 배송 단건(구간 포함). 배송담당자가 담당 구간이 없는 배송을 보면 FORBIDDEN */
    public DeliveryResponse getDelivery(UserContext user, UUID deliveryId) {
        DeliverySummaryResult summary = deliveryQueryRepository.findSummary(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        List<DeliveryRouteResult> routes = deliveryQueryRepository.findRoutes(deliveryId);
        if (isScopedToOwnRoutes(user) && routes.stream().noneMatch(route -> user.userId().equals(route.managerId()))) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return new DeliveryResponse(summary, routes);
    }

    /** I-10 배송 조회(order 전용) */
    public DeliveryStatusResult getDeliveryStatus(UUID deliveryId) {
        return deliveryQueryRepository.findStatus(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }

    private boolean isScopedToOwnRoutes(UserContext user) {
        return user.deliveryManager() && !user.isMaster();
    }
}
