package com.sparta.logistics.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Note: 기본 스캔 범위는 이 클래스의 패키지(com.sparta.logistics.user)라서 common 모듈의 빈을 찾지 못한다. 상위 패키지로 넓혀 common의 예외 처리기 등을 함께 등록한다.
@SpringBootApplication(scanBasePackages = "com.sparta.logistics")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
