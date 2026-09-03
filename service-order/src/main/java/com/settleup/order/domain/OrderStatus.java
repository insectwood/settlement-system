package com.settleup.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    CANCELLED,
    CONFIRMED,
    CANCEL_REQUESTED,
    CREATED;

    private Set<OrderStatus> allowedNext;

    static {
        CREATED.allowedNext = EnumSet.of(CONFIRMED, CANCELLED);
        CONFIRMED.allowedNext = EnumSet.of(CANCEL_REQUESTED);
        CANCEL_REQUESTED.allowedNext = EnumSet.of(CANCELLED, CONFIRMED);
        CANCELLED.allowedNext = EnumSet.noneOf(OrderStatus.class);
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext.contains(next);
    }

    public boolean isTerminal() {
        return allowedNext.isEmpty();
    }
}
