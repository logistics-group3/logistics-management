package com.sparta.logistics.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * delivery-service — 배송·배송 경로 기록 생성, 담당자 배정, 상태 관리
 * 공통 인프라 단계: 기동 + Eureka 등록까지만. 도메인 코드는 서비스 담당자가 추가한다.
 */
// common 모듈(com.sparta.logistics.common)의 설정·예외 처리까지 스캔
@SpringBootApplication(scanBasePackages = "com.sparta.logistics")
@EnableFeignClients
public class DeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class, args);
    }
}
