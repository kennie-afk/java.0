package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One STK push attempt, for either an order or a platform invoice -- see
 * {@link Purpose}. Two of these can exist for the same order/invoice (a
 * customer who cancels the PIN prompt and tries again), but only one may
 * ever be PENDING at a time; enforced in code, not by a constraint, because
 * "no other PENDING row for this reference" isn't expressible as a simple
 * unique index.
 */
@Entity
@Table(name = "mpesa_payments")
public class MpesaPayment {

    public enum Purpose { ORDER, INVOICE }

    public enum Status { PENDING, SUCCESS, FAILED }

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String purpose;
    @Column(name = "reference_id", nullable = false) private UUID referenceId;
    @Column(nullable = false) private String msisdn;
    /** What actually moved through M-Pesa: the due amount rounded UP to whole shillings. */
    @Column(name = "amount_cents", nullable = false) private long amountCents;
    /** What the order or invoice asked for, exact. {@code amountCents - dueCents} is the rounding. */
    @Column(name = "due_cents", nullable = false) private long dueCents;
    @Column(name = "merchant_request_id") private String merchantRequestId;
    @Column(name = "checkout_request_id") private String checkoutRequestId;
    @Column(name = "mpesa_receipt_number") private String mpesaReceiptNumber;
    @Column(nullable = false) private String status = Status.PENDING.name();
    @Column(name = "result_desc") private String resultDesc;
    @Column(name = "initiated_at", nullable = false) private Instant initiatedAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public Purpose getPurpose() { return Purpose.valueOf(purpose); }
    public void setPurpose(Purpose v) { this.purpose = v.name(); }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID v) { this.referenceId = v; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String v) { this.msisdn = v; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long v) { this.amountCents = v; }
    public long getDueCents() { return dueCents; }
    public void setDueCents(long v) { this.dueCents = v; }
    public String getMerchantRequestId() { return merchantRequestId; }
    public void setMerchantRequestId(String v) { this.merchantRequestId = v; }
    public String getCheckoutRequestId() { return checkoutRequestId; }
    public void setCheckoutRequestId(String v) { this.checkoutRequestId = v; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public void setMpesaReceiptNumber(String v) { this.mpesaReceiptNumber = v; }
    public Status getStatus() { return Status.valueOf(status); }
    public void setStatus(Status v) { this.status = v.name(); }
    public String getResultDesc() { return resultDesc; }
    public void setResultDesc(String v) { this.resultDesc = v; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant v) { this.initiatedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
}
