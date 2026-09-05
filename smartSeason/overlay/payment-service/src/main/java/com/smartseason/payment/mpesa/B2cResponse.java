package com.smartseason.payment.mpesa;

public record B2cResponse(
        boolean accepted,
        String conversationId,
        String originatorConversationId,
        String responseCode,
        String responseDescription) {
}
