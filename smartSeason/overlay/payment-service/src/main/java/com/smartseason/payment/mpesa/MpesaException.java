package com.smartseason.payment.mpesa;

public class MpesaException extends RuntimeException {

    public MpesaException(String message) {
        super(message);
    }

    public MpesaException(String message, Throwable cause) {
        super(message, cause);
    }
}
