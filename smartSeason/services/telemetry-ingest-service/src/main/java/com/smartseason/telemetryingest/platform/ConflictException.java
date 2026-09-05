package com.smartseason.telemetryingest.platform;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
