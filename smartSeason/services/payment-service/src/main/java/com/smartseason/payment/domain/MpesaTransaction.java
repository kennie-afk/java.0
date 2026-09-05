package com.smartseason.payment.domain;

import com.smartseason.payment.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mpesa_transactions", indexes = {
        @Index(name = "ix_mpesa_transactions_payment_intent_id", columnList = "payment_intent_id"),
        @Index(name = "ix_mpesa_transactions_merchant_request_id", columnList = "merchant_request_id"),
        @Index(name = "ix_mpesa_transactions_checkout_request_id", columnList = "checkout_request_id"),
        @Index(name = "ix_mpesa_transactions_mpesa_receipt_number", columnList = "mpesa_receipt_number"),
        @Index(name = "ix_mpesa_transactions_phone_number", columnList = "phone_number")
})
public class MpesaTransaction extends BaseEntity {

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "merchant_request_id")
    private String merchantRequestId;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "mpesa_receipt_number", unique = true)
    private String mpesaReceiptNumber;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "transaction_date")
    private Instant transactionDate;

    @Column(name = "account_reference")
    private String accountReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_callback", columnDefinition = "jsonb")
    private String rawCallback;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(UUID paymentIntentId) { this.paymentIntentId = paymentIntentId; }

    public String getMerchantRequestId() { return merchantRequestId; }
    public void setMerchantRequestId(String merchantRequestId) { this.merchantRequestId = merchantRequestId; }

    public String getCheckoutRequestId() { return checkoutRequestId; }
    public void setCheckoutRequestId(String checkoutRequestId) { this.checkoutRequestId = checkoutRequestId; }

    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public void setMpesaReceiptNumber(String mpesaReceiptNumber) { this.mpesaReceiptNumber = mpesaReceiptNumber; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public Integer getResultCode() { return resultCode; }
    public void setResultCode(Integer resultCode) { this.resultCode = resultCode; }

    public String getResultDesc() { return resultDesc; }
    public void setResultDesc(String resultDesc) { this.resultDesc = resultDesc; }

    public Instant getTransactionDate() { return transactionDate; }
    public void setTransactionDate(Instant transactionDate) { this.transactionDate = transactionDate; }

    public String getAccountReference() { return accountReference; }
    public void setAccountReference(String accountReference) { this.accountReference = accountReference; }

    public String getRawCallback() { return rawCallback; }
    public void setRawCallback(String rawCallback) { this.rawCallback = rawCallback; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum TransactionType { STK_PUSH, C2B, B2C, REVERSAL, BALANCE }

    public enum Status { INITIATED, PENDING, SUCCESS, FAILED, TIMEOUT }

}
