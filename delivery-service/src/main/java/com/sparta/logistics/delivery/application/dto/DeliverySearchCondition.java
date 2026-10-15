package com.sparta.logistics.delivery.application.dto;

import com.sparta.logistics.delivery.domain.DeliveryStatus;
import java.util.UUID;

/**
 * D-01 배송 검색 조건. 값이 없는 조건은 쓰지 않는다.
 * 조회 저장소(DeliveryQueryRepository)가 받는 타입이다.
 *
 * @param managerId      검색 파라미터. 이 사람이 담당하는 구간이 있는 배송
 * @param scopeManagerId 조회 범위. 배송담당자는 본인 담당 구간이 있는 배송만 본다(SA 8.4). application이 채운다
 */
public record DeliverySearchCondition(
        DeliveryStatus status,
        UUID departureHubId,
        UUID destinationHubId,
        UUID managerId,
        UUID orderId,
        UUID supplierCompanyId,
        UUID receiverCompanyId,
        UUID scopeManagerId
) {

    public DeliverySearchCondition limitToManager(UUID deliveryManagerId) {
        return new DeliverySearchCondition(status, departureHubId, destinationHubId, managerId, orderId,
                supplierCompanyId, receiverCompanyId, deliveryManagerId);
    }
}
