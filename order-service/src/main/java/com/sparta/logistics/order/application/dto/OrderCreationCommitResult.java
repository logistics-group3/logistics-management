package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.order.domain.Order;

public record OrderCreationCommitResult(Outcome outcome, OrderResult order) {

    public enum Outcome { COMMITTED, NOT_COMMITTED, UNKNOWN }

    public static OrderCreationCommitResult from(Order order) {
        return new OrderCreationCommitResult(Outcome.COMMITTED, OrderResult.from(order));
    }

    public static OrderCreationCommitResult notCommitted() {
        return new OrderCreationCommitResult(Outcome.NOT_COMMITTED, null);
    }

    public static OrderCreationCommitResult unknown() {
        return new OrderCreationCommitResult(Outcome.UNKNOWN, null);
    }
}
