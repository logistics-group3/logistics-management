package com.sparta.logistics.delivery.application.assignment;

import java.util.Collection;

/**
 * 배정 결과를 order에 알린다(I-20, 결정 로그 29). Slack은 order만 보내므로 delivery는 결과만 넘긴다(ADR 0016).
 * Note: 구현은 배정 스레드를 막지 않도록 따로 실행하고, 실패해도 다시 보내지 않는다. 받는 쪽이 발송했는지 알 수 없는 요청을
 * 다시 보내면 DM이 두 번 갈 수 있다(ADR 0016 7번). 메시지 큐로 바꿀 때는 이 구현만 바꾼다.
 */
public interface AssignmentNotifier {

    /** 배정 대기 구간이 모두 채워진 배송. order가 주문이 유효하면 담당자에게 DM을 보낸다. */
    void notifyAssigned(Collection<AssignedDelivery> deliveries);

    /** 수동 지정으로 넘긴 배송. order가 MASTER에게 지정을 요청한다. */
    void notifyManualRequired(Collection<AssignedDelivery> deliveries);
}
