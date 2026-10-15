package com.sparta.logistics.delivery.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.delivery.application.dto.DeliveryResult;
import com.sparta.logistics.delivery.application.dto.DeliveryStatusResult;
import com.sparta.logistics.delivery.application.dto.DeliverySummaryResult;
import com.sparta.logistics.delivery.domain.Delivery;
import com.sparta.logistics.delivery.domain.DeliveryErrorCode;
import com.sparta.logistics.delivery.domain.DeliveryRepository;
import com.sparta.logistics.delivery.domain.DeliverySearchCondition;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배송 조회(D-01, D-02, I-10). 조회 범위는 SA 8.4: MASTER·일반 USER 전체, 배송담당자는 담당 구간이 있는 배송만. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;

    /** D-01 배송 검색. 배송담당자는 조회 범위를 조건에 넣어 DB에서 거른다(가져온 뒤 거르면 페이지·count가 틀어진다) */
    public Page<DeliverySummaryResult> search(UserContext user, DeliverySearchCondition condition, Pageable pageable) {
        DeliverySearchCondition scoped = isScopedToOwnRoutes(user) ? condition.limitToManager(user.userId()) : condition;
        return deliveryRepository.search(scoped, pageable).map(DeliverySummaryResult::from);
    }

    /** D-02 배송 단건(구간 포함). 배송담당자가 담당 구간이 없는 배송을 보면 FORBIDDEN */
    public DeliveryResult getDelivery(UserContext user, UUID deliveryId) {
        Delivery delivery = findDelivery(deliveryId);
        if (isScopedToOwnRoutes(user) && !delivery.hasRouteManagedBy(user.userId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return DeliveryResult.from(delivery);
    }

    /** I-10 배송 조회(order 전용) */
    public DeliveryStatusResult getDeliveryStatus(UUID deliveryId) {
        return DeliveryStatusResult.from(findDelivery(deliveryId));
    }

    private Delivery findDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }

    private boolean isScopedToOwnRoutes(UserContext user) {
        return user.deliveryManager() && !user.isMaster();
    }
}
