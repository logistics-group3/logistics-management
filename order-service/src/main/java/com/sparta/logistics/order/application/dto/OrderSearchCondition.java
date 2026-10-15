package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.domain.OrderSearchCriteria;
import com.sparta.logistics.order.domain.OrderStatus;
import java.time.LocalDate;
import java.util.UUID;

/** O-02 사용자가 지정하는 검색 조건. 주문자 범위는 입력으로 받지 않는다. */
public record OrderSearchCondition(OrderStatus status, UUID supplierCompanyId, UUID receiverCompanyId,
                                   UUID productId, LocalDate startDate, LocalDate endDate) {

    public OrderSearchCondition {
        if (!validYear(startDate) || !validYear(endDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "검색 날짜는 0001~9999년 범위여야 합니다.");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "주문일 시작은 종료보다 늦을 수 없습니다.");
        }
    }

    public OrderSearchCriteria forOrderer(UUID ordererId) {
        // Note: 종료일 다음 날 00:00 미만으로 비교해 종료일의 마지막 소수 초까지 포함한다.
        return new OrderSearchCriteria(ordererId, status, supplierCompanyId, receiverCompanyId, productId,
                startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay());
    }

    private static boolean validYear(LocalDate date) {
        return date == null || (date.getYear() >= 1 && date.getYear() <= 9999);
    }
}
