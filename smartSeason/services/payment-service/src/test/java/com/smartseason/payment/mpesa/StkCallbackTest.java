package com.smartseason.payment.mpesa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StkCallbackTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SUCCESS = """
            {"Body":{"stkCallback":{
              "MerchantRequestID":"29115-34620561-1",
              "CheckoutRequestID":"ws_CO_191220191020363925",
              "ResultCode":0,
              "ResultDesc":"The service request is processed successfully.",
              "CallbackMetadata":{"Item":[
                {"Name":"Amount","Value":1000},
                {"Name":"MpesaReceiptNumber","Value":"NLJ7RT61SV"},
                {"Name":"TransactionDate","Value":20191219102115},
                {"Name":"PhoneNumber","Value":254712345678}
              ]}}}}
            """;

    private static final String CANCELLED = """
            {"Body":{"stkCallback":{
              "MerchantRequestID":"29115-34620561-1",
              "CheckoutRequestID":"ws_CO_191220191020363925",
              "ResultCode":1032,
              "ResultDesc":"Request cancelled by user"}}}
            """;

    @Test
    @DisplayName("a successful callback yields the receipt, amount and payer")
    void parsesSuccess() throws Exception {
        StkCallback callback = mapper.readValue(SUCCESS, StkCallback.class);

        assertThat(callback.succeeded()).isTrue();
        assertThat(callback.receiptNumber()).isEqualTo("NLJ7RT61SV");
        assertThat(callback.amount()).isEqualByComparingTo("1000");
        assertThat(callback.phoneNumber()).isEqualTo("254712345678");
        assertThat(callback.detail().checkoutRequestId()).isEqualTo("ws_CO_191220191020363925");
    }

    @Test
    @DisplayName("a cancelled payment is not treated as a success and carries no receipt")
    void parsesCancellation() throws Exception {
        StkCallback callback = mapper.readValue(CANCELLED, StkCallback.class);

        assertThat(callback.succeeded()).isFalse();
        assertThat(callback.detail().resultCode()).isEqualTo(1032);
        assertThat(callback.receiptNumber()).isNull();
        assertThat(callback.amount()).isNull();
    }

    @Test
    @DisplayName("unknown fields Safaricom may add do not break parsing")
    void toleratesUnknownFields() throws Exception {
        String withExtra = SUCCESS.replace("\"ResultCode\":0",
                "\"ResultCode\":0,\"SomeNewFieldSafaricomAdded\":\"x\"");

        assertThat(mapper.readValue(withExtra, StkCallback.class).succeeded()).isTrue();
    }

    @Test
    @DisplayName("an empty body parses without throwing and reports no detail")
    void toleratesEmptyBody() throws Exception {
        StkCallback callback = mapper.readValue("{}", StkCallback.class);

        assertThat(callback.detail()).isNull();
        assertThat(callback.succeeded()).isFalse();
    }
}
