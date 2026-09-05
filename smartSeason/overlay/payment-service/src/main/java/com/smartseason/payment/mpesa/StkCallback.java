package com.smartseason.payment.mpesa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StkCallback(@JsonProperty("Body") Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(@JsonProperty("stkCallback") StkCallbackDetail stkCallback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StkCallbackDetail(
            @JsonProperty("MerchantRequestID") String merchantRequestId,
            @JsonProperty("CheckoutRequestID") String checkoutRequestId,
            @JsonProperty("ResultCode") Integer resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackMetadata(@JsonProperty("Item") List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("Name") String name,
            @JsonProperty("Value") Object value) {
    }

    public boolean succeeded() {
        return detail() != null && Integer.valueOf(0).equals(detail().resultCode());
    }

    public StkCallbackDetail detail() {
        return body == null ? null : body.stkCallback();
    }

    public String receiptNumber() {
        return metadataValue("MpesaReceiptNumber") instanceof String value ? value : null;
    }

    public BigDecimal amount() {
        Object value = metadataValue("Amount");
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    public String phoneNumber() {
        Object value = metadataValue("PhoneNumber");
        return value == null ? null : String.valueOf(value);
    }

    private Object metadataValue(String name) {
        StkCallbackDetail detail = detail();
        if (detail == null || detail.callbackMetadata() == null
                || detail.callbackMetadata().item() == null) {
            return null;
        }
        return detail.callbackMetadata().item().stream()
                .filter(item -> name.equals(item.name()))
                .map(Item::value)
                .findFirst()
                .orElse(null);
    }
}
