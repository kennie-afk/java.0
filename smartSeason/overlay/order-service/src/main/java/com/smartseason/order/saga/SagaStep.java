package com.smartseason.order.saga;

public enum SagaStep {
    RESERVE_STOCK,
    AUTHORISE_PAYMENT,
    CONFIRM_ORDER,
    ARRANGE_TRANSPORT,
    COMPLETE;

    public SagaStep next() {
        return switch (this) {
            case RESERVE_STOCK -> AUTHORISE_PAYMENT;
            case AUTHORISE_PAYMENT -> CONFIRM_ORDER;
            case CONFIRM_ORDER -> ARRANGE_TRANSPORT;
            case ARRANGE_TRANSPORT, COMPLETE -> COMPLETE;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETE;
    }
}
