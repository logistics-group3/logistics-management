package com.sparta.logistics.hub.application;

import java.util.UUID;

/** company-service 상품 조회 포트(SA 6.3 I-08). 구현은 infrastructure/client가 맡는다. */
public interface ProductReader {

    /** 상품이 있으면 조용히 끝나고, 없으면 BusinessException(PRODUCT_NOT_FOUND 404)을 던진다. 상대 장애는 503이다. */
    void checkExists(UUID productId);

    /** 상품이 속한 업체 ID. 없으면 BusinessException(PRODUCT_NOT_FOUND 404)을 던진다. 상대 장애는 503이다. */
    UUID getCompanyId(UUID productId);
}
