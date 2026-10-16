package com.sparta.logistics.delivery.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sparta.logistics.delivery.application.DeliveryCreationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 부하 테스트 API는 배송 생성을 외부로 여는 것이라 기본으로 꺼져 있어야 한다. 켜는 조건만 확인한다.
 * Note: ApplicationContextRunner는 필요한 빈만 올린 작은 컨텍스트로 조건부 등록(@ConditionalOnProperty)을 확인한다.
 * 서비스 전체를 띄우는 통합 테스트보다 빠르고, 프로퍼티를 바꿔도 다른 테스트의 컨텍스트 캐시를 깨지 않는다.
 */
class LoadTestDeliveryControllerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DeliveryCreationService.class, () -> mock(DeliveryCreationService.class))
            .withUserConfiguration(LoadTestDeliveryController.class);

    @Test
    @DisplayName("프로퍼티가 없으면 부하 테스트 API가 등록되지 않는다")
    void disabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LoadTestDeliveryController.class));
    }

    @Test
    @DisplayName("delivery.load-test.enabled=false면 등록되지 않는다")
    void disabledExplicitly() {
        runner.withPropertyValues("delivery.load-test.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LoadTestDeliveryController.class));
    }

    @Test
    @DisplayName("delivery.load-test.enabled=true일 때만 등록된다")
    void enabledByProperty() {
        runner.withPropertyValues("delivery.load-test.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LoadTestDeliveryController.class));
    }
}
