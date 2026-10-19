package com.sparta.logistics.delivery.infrastructure.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * delivery가 부르는 내부 API(user, hub)의 재시도 설정(결정 로그 26). 연결 실패만 한 번 더 시도한다.
 * Note: 일부러 @Configuration을 붙이지 않는다. 빈으로 등록되면 모든 FeignClient에 적용된다. 필요한 클라이언트의
 * {@code @FeignClient(configuration = ...)}에만 붙인다(common IdempotentRetryConfig와 같은 방식).
 */
public class ConnectFailureRetryConfig {

    @Bean
    public Retryer retryer() {
        return new ConnectFailureRetryer(2, 100);
    }
}
