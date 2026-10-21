package com.sparta.logistics.user.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param type Gateway 권한 재확인 캐시 저장소(local, redis). Gateway의 auth.user-cache와 같은 값을 준다(AUTH_USER_CACHE). */
@ConfigurationProperties(prefix = "user.auth-cache")
public record UserAuthCacheProperties(@DefaultValue("local") String type) {

    public boolean redis() {
        return "redis".equalsIgnoreCase(type);
    }
}
