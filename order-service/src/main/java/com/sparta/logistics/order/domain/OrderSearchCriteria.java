package com.sparta.logistics.order.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/** O-02 저장소 조건. ordererId는 인증 사용자로 결정하고 날짜는 [from, before) 범위로 변환한다. */
public record OrderSearchCriteria(UUID ordererId, OrderStatus status, UUID supplierCompanyId,
                                  UUID receiverCompanyId, UUID productId,
                                  LocalDateTime createdFrom, LocalDateTime createdBefore) {
}
