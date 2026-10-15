package com.sparta.logistics.delivery.infrastructure.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sparta.logistics.delivery")
public class FeignConfig {
}
