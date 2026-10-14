package com.sparta.logistics.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Note: Jackson은 기본적으로 Enum에 숫자를 넣으면 선언 순서(ordinal)로 바꿔 준다. {@code "affiliationType": 1}이 HUB가 되는 식이다.
     * Enum 선언 순서가 바뀌면 같은 숫자가 다른 값이 되므로 문자열만 받는다(SA 8.5 "정의된 값만 허용").
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictEnumCustomizer() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    }
}
