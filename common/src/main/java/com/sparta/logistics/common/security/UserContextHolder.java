package com.sparta.logistics.common.security;

import java.util.Optional;

/** 요청 스레드의 현재 사용자. UserContextInterceptor가 설정하고 요청이 끝나면 지운다. */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static Optional<UserContext> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static String currentUsernameOrSystem() {
        return get().map(UserContext::username).orElse(UserHeaders.SYSTEM_USER);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
