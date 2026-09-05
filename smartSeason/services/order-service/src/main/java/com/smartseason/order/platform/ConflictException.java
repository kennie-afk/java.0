package com.smartseason.order.platform;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
