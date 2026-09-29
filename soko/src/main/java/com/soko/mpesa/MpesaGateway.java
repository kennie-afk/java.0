package com.soko.mpesa;

/** Only STK push is needed here -- Soko collects from customers, it never pays anyone out. */
public interface MpesaGateway {

    StkPushResponse stkPush(StkPushRequest request);

    boolean verifyCallbackSignature(String rawBody, String signature);
}
