package com.sparta.logistics.delivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.infrastructure.client.UserClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * UserClientAdapter에 서킷 브레이커와 fallback이 실제로 걸리는지(AOP 프록시) 확인한다(결정 로그 26).
 * 테스트는 config-server를 쓰지 않아 Resilience4j 기본 설정으로 돈다. 임계값 효과는 부하 실험(fault-isolation.md)에서 본다.
 * 컨텍스트가 하나 더 생기므로 공유 테스트 PostgreSQL 연결을 아끼려 풀을 작게 둔다(TS-020).
 */
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
class UserClientResilienceIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean
    private UserClient userClient;
    @Autowired
    private DeliveryManagerReader deliveryManagerReader;
    @Autowired
    private RecipientReader recipientReader;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void closeCircuit() {
        circuitBreakerRegistry.circuitBreaker("user-service").reset();
    }

    @Test
    @DisplayName("user 장애면 수령인 Slack ID 없이 진행한다(부분 저하)")
    void slackIdDegradesWhenUserIsDown() {
        given(userClient.getUser(any())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertThat(recipientReader.findSlackId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("user 장애면 배송담당자 후보 조회는 대신할 값이 없어 503이다")
    void managersFailWhenUserIsDown() {
        given(userClient.getDeliveryManagers(any())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> deliveryManagerReader.findIdsByHub(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("서킷이 열리면 user를 부르지 않고 바로 503이다")
    void openCircuitSkipsCall() {
        circuitBreakerRegistry.circuitBreaker("user-service").transitionToOpenState();

        assertThatThrownBy(() -> deliveryManagerReader.findIdsByHub(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(userClient, never()).getDeliveryManagers(any());
    }
}
