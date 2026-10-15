package com.sparta.logistics.order.infrastructure.client.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/** OrderService의 보상 재시도와 겹치지 않도록 I-13 클라이언트에만 적용한다. */
public class DeliveryDeleteFeignConfig {

    // Note: @Configuration을 붙여 전역 스캔에 포함하면 GET·배송 생성의 멱등 재시도까지 바뀔 수 있다.
    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }
}
