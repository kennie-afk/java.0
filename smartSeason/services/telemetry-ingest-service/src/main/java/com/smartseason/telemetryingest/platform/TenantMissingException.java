package com.smartseason.telemetryingest.platform;

public class TenantMissingException extends RuntimeException {

    public TenantMissingException() {
        super("No tenant bound to the current request");
    }
}
