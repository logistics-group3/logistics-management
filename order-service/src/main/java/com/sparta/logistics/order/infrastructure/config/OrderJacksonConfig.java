package com.sparta.logistics.order.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrderJacksonConfig {

    /** 정수 수량·원화 단가 계약. 요청 수량과 Feign 응답 가격에서 소수 부분이 조용히 버려지는 것을 막는다. */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictIntegerCustomizer() {
        return builder -> builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
