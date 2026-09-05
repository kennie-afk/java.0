package com.smartseason.notification.dispatch;

public enum DeliveryChannel {
    SMS, USSD, PUSH, WHATSAPP, EMAIL;

    public boolean respectsQuietHours() {
        return this == SMS || this == PUSH || this == WHATSAPP;
    }
}
