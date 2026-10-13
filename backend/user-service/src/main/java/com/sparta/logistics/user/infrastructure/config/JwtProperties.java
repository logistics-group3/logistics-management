package com.sparta.logistics.user.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * jwt.* 중 실행 중에 바꿔도 되는 값 (config/user-service.yml).
 * <p>JavaBean(setter) 방식이라 config/ 저장 → config-server 자동 refresh로 재시작 없이 바뀐다.
 * 사용하는 쪽은 토큰을 발급할 때마다 getter로 읽는다.
 * <p>{@code jwt.secret}은 여기 두지 않는다: gateway(검증)와 동시에 바뀌어야 하므로 refresh 대상이 아니다
 * ({@link com.sparta.logistics.user.infrastructure.jwt.JwtTokenProvider} 생성자에서 한 번 읽음, 교체는 재시작).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Access Token 유효시간(분). SA 8.2 기본 60분 */
    private long expirationMinutes = 60;
}
