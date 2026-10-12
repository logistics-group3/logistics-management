package com.sparta.logistics.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모든 FeignClient에 공통 적용 (헤더 전달 + 에러 변환). 타임아웃은 config/application.yml.
 * FeignClient 인터페이스는 각 서비스 {@code infrastructure/client}에 두고,
 * Application 클래스에 {@code @EnableFeignClients}를 붙인다.
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor feignHeaderInterceptor() {
        return new FeignHeaderInterceptor();
    }

    @Bean
    public ErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return new FeignErrorDecoder(objectMapper);
    }
}
