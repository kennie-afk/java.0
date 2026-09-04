package com.kenyarealestate.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class C2bCallbackRequest {

    @JsonProperty("TransactionType")  private String transactionType;
    @JsonProperty("TransID")          private String transId;
    @JsonProperty("TransTime")        private String transTime;
    @JsonProperty("TransAmount")      private String transAmount;
    @JsonProperty("BusinessShortCode") private String businessShortCode;
    @JsonProperty("BillRefNumber")    private String billRefNumber;
    @JsonProperty("InvoiceNumber")    private String invoiceNumber;
    @JsonProperty("OrgAccountBalance") private String orgAccountBalance;
    @JsonProperty("ThirdPartyTransID") private String thirdPartyTransId;
    @JsonProperty("MSISDN")           private String msisdn;
    @JsonProperty("FirstName")        private String firstName;
    @JsonProperty("MiddleName")       private String middleName;
    @JsonProperty("LastName")         private String lastName;

    public String reference() {
        return billRefNumber != null && !billRefNumber.isBlank() ? billRefNumber.trim() : invoiceNumber;
    }

    public String payerName() {
        return java.util.stream.Stream.of(firstName, middleName, lastName)
                .filter(n -> n != null && !n.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse(null);
    }
}
