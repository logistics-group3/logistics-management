package com.sparta.logistics.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * OpenFeign 공통 설정. spring-cloud-starter-openfeign 이 있는 MVC 서비스에서만 켜진다.
 * 타임아웃(connect 2초 / read 5초)은 각 서비스 application.yml 의 spring.cloud.openfeign.client.config.default 에 있다.
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonFeignAutoConfiguration {

    @Bean
    public RequestInterceptor feignHeaderInterceptor() {
        return new FeignHeaderInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder commonFeignErrorDecoder(ObjectProvider<ObjectMapper> objectMapper) {
        return new CommonFeignErrorDecoder(objectMapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()));
    }

    @Bean
    public FeignExceptionHandler feignExceptionHandler() {
        return new FeignExceptionHandler();
    }
}
