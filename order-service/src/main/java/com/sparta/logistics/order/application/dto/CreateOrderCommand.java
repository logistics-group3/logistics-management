package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import java.util.UUID;

/** O-01 입력. 주문자와 상품 가격은 요청에서 받지 않는다. */
public record CreateOrderCommand(UUID supplierCompanyId, UUID receiverCompanyId, UUID productId,
                                 int quantity, String requestNote) {

    public CreateOrderCommand {
        if (supplierCompanyId == null || receiverCompanyId == null || productId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "업체와 상품 ID는 필수입니다.");
        }
        if (supplierCompanyId.equals(receiverCompanyId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "공급업체와 수령업체는 달라야 합니다.");
        }
        if (quantity < 1 || (requestNote != null && requestNote.length() > 500)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "수량은 1 이상이고 요청사항은 500자 이내여야 합니다.");
        }
    }
}
