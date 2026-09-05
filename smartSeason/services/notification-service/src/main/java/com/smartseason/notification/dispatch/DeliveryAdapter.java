package com.smartseason.notification.dispatch;

public interface DeliveryAdapter {

    DeliveryChannel channel();

    DeliveryResult send(String destination, String subject, String body);
}
