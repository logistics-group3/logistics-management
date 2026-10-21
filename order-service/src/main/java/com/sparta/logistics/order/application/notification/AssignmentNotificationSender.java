package com.sparta.logistics.order.application.notification;

/** 배정 결과 알림을 요청 스레드 밖에서 처리하도록 넘긴다. 실행기가 가득 차면 예외로 알린다. */
public interface AssignmentNotificationSender {

    void send(AssignmentNotification notification);
}
