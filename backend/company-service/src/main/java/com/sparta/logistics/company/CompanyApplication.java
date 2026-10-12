package com.sparta.logistics.company;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * company-service — 업체 CRUD, 상품(업체 하위 리소스) CRUD
 * 공통 인프라 단계: 기동 + Eureka 등록까지만. 도메인 코드는 서비스 담당자가 추가한다.
 */
// common 모듈(com.sparta.logistics.common)의 설정·예외 처리까지 스캔
@SpringBootApplication(scanBasePackages = "com.sparta.logistics")
@EnableFeignClients
public class CompanyApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyApplication.class, args);
    }
}
