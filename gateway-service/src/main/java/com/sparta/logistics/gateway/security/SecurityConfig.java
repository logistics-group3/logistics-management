package com.sparta.logistics.gateway.security;

import com.sparta.logistics.gateway.support.ErrorResponseWriter;
import com.sparta.logistics.gateway.support.GatewayErrorCode;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpBasicSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.LogoutSpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Gateway 인증 구성(SA 8.3). 단계별 담당:
 * 1~2단계 UserHeaderSanitizingFilter(Security 앞) / 3단계 securityMatcher(화이트리스트 제외) /
 * 4단계 oauth2ResourceServer + ReactiveJwtDecoder / 5단계 UserVerifyingJwtConverter / 6단계 UserHeaderInjectionFilter.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Note: Gateway는 토큰만 보는 무상태 API 서버라서 세션, CSRF, 폼 로그인, Basic 인증을 모두 끈다.
     * 화이트리스트는 permitAll이 아니라 securityMatcher에서 아예 제외한다. permitAll이면 만료된 토큰을 붙인 로그인 요청도
     * 토큰 검증에 걸려 401이 되기 때문이다.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, AuthProperties properties,
                                                         UserLookup userLookup, ErrorResponseWriter errorWriter) {
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(errorWriter);
        return http
                .securityMatcher(new NegatedServerWebExchangeMatcher(
                        ServerWebExchangeMatchers.pathMatchers(properties.whitelist().toArray(String[]::new))))
                .csrf(CsrfSpec::disable)
                .formLogin(FormLoginSpec::disable)
                .httpBasic(HttpBasicSpec::disable)
                .logout(LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new UserVerifyingJwtConverter(userLookup)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((exchange, denied) -> errorWriter.write(exchange, GatewayErrorCode.FORBIDDEN)))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterAfter(new UserHeaderInjectionFilter(), SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }

    /** user-service가 같은 secret으로 서명한 HS256 토큰의 서명과 만료를 검증한다. */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties) {
        SecretKeySpec key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
