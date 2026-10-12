package com.sparta.logistics.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * hub-service — 허브·허브 간 경로 조회, 허브별 상품 재고(입고·차감·복원)
 * 공통 인프라 단계: 기동 + Eureka 등록까지만. 도메인 코드는 서비스 담당자가 추가한다.
 */
// common 모듈(com.sparta.logistics.common)의 설정·예외 처리까지 스캔
@SpringBootApplication(scanBasePackages = "com.sparta.logistics")
@EnableFeignClients
public class HubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubApplication.class, args);
    }
}
