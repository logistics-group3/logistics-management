package com.sparta.logistics.delivery.application.assignment;

/**
 * I-21 응답을 배송 쪽 판단으로 옮긴 값.
 *
 * @param result      order 생성 시도 결과(COMPLETED, PROCESSING, ABORTED, RECOVERY_REQUIRED, NONE)
 * @param orderStatus 주문 상태(ORDERED, CANCELED). 주문이 없으면 null
 */
public record OrderCreationCheck(String result, String orderStatus) {

    /** 주문이 커밋됐고 취소되지 않았다. 이 배송은 확정해 배정한다. */
    public boolean confirmable() {
        return "COMPLETED".equals(result) && "ORDERED".equals(orderStatus);
    }

    /** 주문이 아직 처리 중이다. 다음 확인에서 다시 본다. */
    public boolean inProgress() {
        return "PROCESSING".equals(result);
    }
}
