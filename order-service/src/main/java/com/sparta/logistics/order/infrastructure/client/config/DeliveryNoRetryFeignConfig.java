package com.sparta.logistics.order.infrastructure.client.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/** I-11 취소의 자동 재시도와 I-13 보상의 중첩 재시도를 막는다. */
public class DeliveryNoRetryFeignConfig {

    // Note: @Configuration을 붙여 전역 스캔에 포함하면 GET·배송 생성의 멱등 재시도까지 바뀔 수 있다.
    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }
}
