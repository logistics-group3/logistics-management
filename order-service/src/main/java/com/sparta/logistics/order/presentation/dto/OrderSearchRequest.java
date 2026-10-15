package com.sparta.logistics.order.presentation.dto;

import com.sparta.logistics.order.application.dto.OrderSearchCondition;
import com.sparta.logistics.order.domain.OrderStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/** O-02 선택 검색 파라미터. ordererId는 HTTP 입력으로 받지 않는다. */
public record OrderSearchRequest(OrderStatus status, UUID supplierCompanyId, UUID receiverCompanyId, UUID productId,
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    public OrderSearchCondition toCondition() {
        return new OrderSearchCondition(status, supplierCompanyId, receiverCompanyId, productId, startDate, endDate);
    }
}
