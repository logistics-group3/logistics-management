package com.sparta.logistics.common.auth;

import java.util.List;

/** Gateway → 각 서비스 전달 헤더 (SA 6.1). Gateway 가 클라이언트가 보낸 같은 이름의 헤더를 지우고 다시 넣는다. */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";
    public static final String DELIVERY_MANAGER = "X-Delivery-Manager";

    /** Gateway 가 제거·재주입하고, Feign 이 그대로 전달하는 헤더 목록 */
    public static final List<String> ALL = List.of(USER_ID, USERNAME, USER_ROLE, DELIVERY_MANAGER);

    /** 헤더가 없는 내부 시스템 작업의 Audit 기록값 */
    public static final String SYSTEM = "SYSTEM";

    private UserHeaders() {
    }
}
