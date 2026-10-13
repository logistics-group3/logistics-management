package com.sparta.logistics.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// common 모듈(com.sparta.logistics.common)의 설정·예외 처리까지 스캔
@SpringBootApplication(scanBasePackages = "com.sparta.logistics")
@ConfigurationPropertiesScan
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
