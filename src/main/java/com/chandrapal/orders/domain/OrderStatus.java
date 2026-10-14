package com.chandrapal.orders.domain;

/**
 * Order lifecycle. Transitions are validated so that a late or duplicate
 * event can never move an order backwards (e.g. CANCELLED -> CONFIRMED).
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case CREATED -> next == CONFIRMED || next == REJECTED || next == CANCELLED;
            case CONFIRMED -> next == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }
}
