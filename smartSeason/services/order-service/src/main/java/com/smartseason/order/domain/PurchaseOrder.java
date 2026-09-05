package com.smartseason.order.domain;

import com.smartseason.order.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "ix_orders_order_number", columnList = "order_number"),
        @Index(name = "ix_orders_buyer_org_id", columnList = "buyer_org_id"),
        @Index(name = "ix_orders_seller_org_id", columnList = "seller_org_id"),
        @Index(name = "ix_orders_payment_intent_id", columnList = "payment_intent_id"),
        @Index(name = "ix_orders_idempotency_key", columnList = "idempotency_key")
})
public class PurchaseOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "buyer_org_id", nullable = false)
    private UUID buyerOrgId;

    @Column(name = "seller_org_id", nullable = false)
    private UUID sellerOrgId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;

    @Column(name = "platform_fee", nullable = false)
    private BigDecimal platformFee;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "delivery_county")
    private String deliveryCounty;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "delivery_lat")
    private BigDecimal deliveryLat;

    @Column(name = "delivery_lng")
    private BigDecimal deliveryLng;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "transport_job_id")
    private UUID transportJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public UUID getBuyerOrgId() { return buyerOrgId; }
    public void setBuyerOrgId(UUID buyerOrgId) { this.buyerOrgId = buyerOrgId; }

    public UUID getSellerOrgId() { return sellerOrgId; }
    public void setSellerOrgId(UUID sellerOrgId) { this.sellerOrgId = sellerOrgId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal platformFee) { this.platformFee = platformFee; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(Instant fulfilledAt) { this.fulfilledAt = fulfilledAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public String getDeliveryCounty() { return deliveryCounty; }
    public void setDeliveryCounty(String deliveryCounty) { this.deliveryCounty = deliveryCounty; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public BigDecimal getDeliveryLat() { return deliveryLat; }
    public void setDeliveryLat(BigDecimal deliveryLat) { this.deliveryLat = deliveryLat; }

    public BigDecimal getDeliveryLng() { return deliveryLng; }
    public void setDeliveryLng(BigDecimal deliveryLng) { this.deliveryLng = deliveryLng; }

    public UUID getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(UUID paymentIntentId) { this.paymentIntentId = paymentIntentId; }

    public UUID getTransportJobId() { return transportJobId; }
    public void setTransportJobId(UUID transportJobId) { this.transportJobId = transportJobId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public enum Status { PENDING_PAYMENT, PAID, CONFIRMED, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED, REFUNDED }

}
