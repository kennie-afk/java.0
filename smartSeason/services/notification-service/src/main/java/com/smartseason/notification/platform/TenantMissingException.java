package com.smartseason.notification.platform;

public class TenantMissingException extends RuntimeException {

    public TenantMissingException() {
        super("No tenant bound to the current request");
    }
}
