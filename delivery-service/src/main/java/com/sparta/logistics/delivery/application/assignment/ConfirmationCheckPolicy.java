package com.sparta.logistics.delivery.application.assignment;

import java.time.Duration;

/**
 * 확정(I-19)을 받지 못한 배송을 order에 물어볼 범위(결정 로그 29).
 *
 * @param after       만든 뒤 이 시간이 지나도 미확정이면 확인한다. 평소 확정은 주문 커밋 직후 수 ms 안에 온다
 * @param giveUpAfter 만든 뒤 이 시간이 지나면 더 묻지 않는다. 생성이 실패한 주문의 고아 배송을 계속 묻지 않게 한다
 * @param batchSize   한 번에 확인할 최대 건수
 */
public record ConfirmationCheckPolicy(Duration after, Duration giveUpAfter, int batchSize) {
}
