package com.sparta.logistics.common.feign;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * GET과 멱등 API 전용 재시도 설정 (최대 3회, ADR-04). 일부러 @Configuration을 붙이지 않았다.
 * 재시도해도 안전한 클라이언트에만 {@code @FeignClient(configuration = IdempotentFeignConfig.class)}로 지정한다.
 * 입고(I-04)처럼 멱등키가 없는 호출은 기본값(재시도 없음)을 유지한다.
 */
public class IdempotentFeignConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100L, 1000L, 3);
    }
}
