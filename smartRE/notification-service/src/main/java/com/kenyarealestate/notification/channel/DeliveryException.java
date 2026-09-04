package com.kenyarealestate.notification.channel;

public class DeliveryException extends Exception {
    public DeliveryException(String message, Throwable cause) { super(message, cause); }
    public DeliveryException(String message) { super(message); }
}
