package com.sparta.logistics.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * common 모듈의 자동 설정 진입점 (ADR-15).
 * AutoConfiguration.imports로 등록하므로 서비스는 의존성만 추가하면 되고, 패키지 스캔 경로를 맞출 필요가 없다.
 * 각 빈은 @ConditionalOnMissingBean이라 서비스가 직접 덮어쓸 수 있다.
 */
@AutoConfiguration
@Import({
        JpaCommonConfig.class,
        QuerydslCommonConfig.class,
        WebMvcCommonConfig.class,
        FeignCommonConfig.class
})
public class CommonAutoConfiguration {
}
