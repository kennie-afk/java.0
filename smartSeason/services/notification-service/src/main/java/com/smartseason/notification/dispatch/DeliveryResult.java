package com.smartseason.notification.dispatch;

public record DeliveryResult(
        boolean accepted,
        String providerRef,
        String detail,
        boolean retryable) {

    public static DeliveryResult accepted(String providerRef) {
        return new DeliveryResult(true, providerRef, null, false);
    }

    public static DeliveryResult rejected(String detail) {
        return new DeliveryResult(false, null, detail, false);
    }

    public static DeliveryResult transientFailure(String detail) {
        return new DeliveryResult(false, null, detail, true);
    }
}
