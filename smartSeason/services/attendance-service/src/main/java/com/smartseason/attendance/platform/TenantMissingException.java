package com.smartseason.attendance.platform;

public class TenantMissingException extends RuntimeException {

    public TenantMissingException() {
        super("No tenant bound to the current request");
    }
}
