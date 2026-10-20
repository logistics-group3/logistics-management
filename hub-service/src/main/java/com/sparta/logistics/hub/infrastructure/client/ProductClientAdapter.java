package com.sparta.logistics.hub.infrastructure.client;

import com.sparta.logistics.hub.application.ProductReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductClientAdapter implements ProductReader {

    private final ProductClient productClient;

    /** 404는 FeignErrorDecoder가 BusinessException(PRODUCT_NOT_FOUND)으로 바꾼다. 응답이 오면 존재하는 상품이다. */
    @Override
    public void checkExists(UUID productId) {
        productClient.getProduct(productId);
    }

    @Override
    public UUID getCompanyId(UUID productId) {
        return productClient.getProduct(productId).companyId();
    }
}
