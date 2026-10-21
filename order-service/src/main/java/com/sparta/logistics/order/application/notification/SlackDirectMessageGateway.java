package com.sparta.logistics.order.application.notification;

public interface SlackDirectMessageGateway {
    /** true는 Slack 발송 확인, false는 수신자 허용 목록에 따른 생략이다. POST는 자동 재시도하지 않는다. */
    boolean send(String email, String message);
}
