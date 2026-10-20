package com.sparta.logistics.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})
public class AuthConfig {

    /**
     * Note: Boot가 만든 WebClient.Builder를 복제(clone)해서 쓴다. @LoadBalanced WebClient.Builder 빈을 새로 정의하면 Boot 기본 빌더가
     * 사라지고, 그 빌더에 baseUrl을 지정하면 같은 빈을 주입받는 다른 코드까지 user-service 주소를 갖게 된다.
     * 타임아웃은 Gateway 라우트 설정(response-timeout)과 별개라서 이 클라이언트에 직접 건다.
     */
    @Bean
    public UserLookup userLookup(WebClient.Builder webClientBuilder,
                                 ReactorLoadBalancerExchangeFilterFunction loadBalancer,
                                 AuthProperties properties,
                                 ObjectProvider<ReactiveRedisConnectionFactory> redisConnectionFactory,
                                 ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.userLookupTimeout().toMillis())
                .responseTimeout(properties.userLookupTimeout());
        WebClient webClient = webClientBuilder.clone()
                .baseUrl("http://user-service")
                .filter(loadBalancer)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        UserServiceClient client = new UserServiceClient(webClient, properties.userLookupTimeout());
        if (properties.userCache() == AuthProperties.UserCacheType.REDIS) {
            return new RedisUserLookup(userSnapshotRedis(redisConnectionFactory.getObject(), objectMapper),
                    client::fetch, properties.userCacheTtl());
        }
        return new CachedUserLookup(client::fetch, properties.userCacheTtl());
    }

    /** 키는 문자열, 값은 UserSnapshot JSON. 타입 정보를 JSON에 넣지 않도록 값 타입을 고정한 직렬화기를 쓴다. */
    private static ReactiveRedisTemplate<String, UserSnapshot> userSnapshotRedis(ReactiveRedisConnectionFactory factory,
                                                                                 ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<UserSnapshot> value = new Jackson2JsonRedisSerializer<>(objectMapper, UserSnapshot.class);
        RedisSerializationContext<String, UserSnapshot> context = RedisSerializationContext
                .<String, UserSnapshot>newSerializationContext(new StringRedisSerializer())
                .value(value)
                .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}
