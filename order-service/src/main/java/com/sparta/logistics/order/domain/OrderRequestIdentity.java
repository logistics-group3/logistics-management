package com.sparta.logistics.order.domain;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;

/** 키는 사용자 범위의 요청 식별자이며 지문은 실제 주문 입력을 구분한다. */
public record OrderRequestIdentity(String key, String requestHash) {

    public static final int MAX_KEY_LENGTH = 128;
    public static final String KEY_PATTERN = "[A-Za-z0-9._:-]+";

    public OrderRequestIdentity {
        if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH || !key.matches(KEY_PATTERN)
                || requestHash == null || !requestHash.matches("v1:[0-9a-f]{64}")) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "주문 멱등키 또는 요청 지문이 올바르지 않습니다.");
        }
    }
}
