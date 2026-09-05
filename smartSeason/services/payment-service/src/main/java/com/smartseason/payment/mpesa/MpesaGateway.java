package com.smartseason.payment.mpesa;

public interface MpesaGateway {

    StkPushResponse stkPush(StkPushRequest request);

    B2cResponse businessToCustomer(B2cRequest request);

    boolean verifyCallbackSignature(String rawBody, String signature);
}
