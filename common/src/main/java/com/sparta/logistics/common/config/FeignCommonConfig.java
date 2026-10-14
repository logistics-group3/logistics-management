package com.sparta.logistics.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.feign.FeignErrorDecoder;
import com.sparta.logistics.common.feign.FeignExceptionHandler;
import com.sparta.logistics.common.feign.FeignHeaderInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 헤더 전달 + 하위 에러 변환. 타임아웃은 각 서비스 application.yml의 spring.cloud.openfeign.client.config로 둔다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "feign.Feign")
class FeignCommonConfig {

    @Bean
    @ConditionalOnMissingBean
    FeignHeaderInterceptor feignHeaderInterceptor() {
        return new FeignHeaderInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    ErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return new FeignErrorDecoder(objectMapper);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import(FeignExceptionHandler.class)
    static class FeignExceptionHandlerConfig {
    }
}
