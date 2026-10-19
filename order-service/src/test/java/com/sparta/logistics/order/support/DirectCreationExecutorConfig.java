package com.sparta.logistics.order.support;

import com.sparta.logistics.order.application.OrderCreationExecutor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 주문 생성의 처리 단계(I-09 → 저장 → 보상)를 요청 스레드에서 바로 실행하게 한다.
 * O-01은 202로 접수하고 뒷단을 별도 스레드에서 처리하는데, 통합 테스트는 응답을 받은 시점에 처리가 끝나 있어야 호출 횟수·DB 상태를
 * 결정적으로 단언할 수 있다. 사용: 테스트 클래스에 {@code @Import(DirectCreationExecutorConfig.class)}.
 * 실제 스레드풀 동작(접수 후 지연·거절)은 OrderCreationAsyncIntegrationTest가 따로 검증한다.
 */
@TestConfiguration
public class DirectCreationExecutorConfig {

    @Bean
    @Primary
    OrderCreationExecutor directOrderCreationExecutor() {
        return OrderCreationExecutor.direct();
    }
}
