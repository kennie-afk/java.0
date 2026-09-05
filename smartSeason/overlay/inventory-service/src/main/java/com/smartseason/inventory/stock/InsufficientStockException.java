package com.smartseason.inventory.stock;

import java.math.BigDecimal;

public class InsufficientStockException extends RuntimeException {

    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientStockException(BigDecimal requested, BigDecimal available) {
        super("Requested %s but only %s is available"
                .formatted(requested.toPlainString(), available.toPlainString()));
        this.requested = requested;
        this.available = available;
    }

    public BigDecimal requested() {
        return requested;
    }

    public BigDecimal available() {
        return available;
    }
}
