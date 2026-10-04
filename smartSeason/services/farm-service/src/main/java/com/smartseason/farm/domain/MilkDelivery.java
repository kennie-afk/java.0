package com.smartseason.farm.domain;

import com.smartseason.farm.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "milk_deliveries", indexes = {
        @Index(name = "ix_milk_deliveries_farm_id", columnList = "farm_id"),
        @Index(name = "ix_milk_deliveries_delivered_on", columnList = "delivered_on"),
        @Index(name = "ix_milk_deliveries_receipt_no", columnList = "receipt_no")
})
public class MilkDelivery extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "delivered_on", nullable = false)
    private LocalDate deliveredOn;

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    @Column(name = "receipt_no")
    private String receiptNo;

    @Column(name = "litres_delivered", nullable = false)
    private BigDecimal litresDelivered;

    @Column(name = "litres_rejected", nullable = false)
    private BigDecimal litresRejected;

    @Column(name = "fat_pct")
    private BigDecimal fatPct;

    @Column(name = "snf_pct")
    private BigDecimal snfPct;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "alcohol_test_passed")
    private Boolean alcoholTestPassed;

    @Column(name = "price_per_litre")
    private BigDecimal pricePerLitre;

    @Column(name = "currency")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public LocalDate getDeliveredOn() { return deliveredOn; }
    public void setDeliveredOn(LocalDate deliveredOn) { this.deliveredOn = deliveredOn; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }

    public BigDecimal getLitresDelivered() { return litresDelivered; }
    public void setLitresDelivered(BigDecimal litresDelivered) { this.litresDelivered = litresDelivered; }

    public BigDecimal getLitresRejected() { return litresRejected; }
    public void setLitresRejected(BigDecimal litresRejected) { this.litresRejected = litresRejected; }

    public BigDecimal getFatPct() { return fatPct; }
    public void setFatPct(BigDecimal fatPct) { this.fatPct = fatPct; }

    public BigDecimal getSnfPct() { return snfPct; }
    public void setSnfPct(BigDecimal snfPct) { this.snfPct = snfPct; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public Boolean getAlcoholTestPassed() { return alcoholTestPassed; }
    public void setAlcoholTestPassed(Boolean alcoholTestPassed) { this.alcoholTestPassed = alcoholTestPassed; }

    public BigDecimal getPricePerLitre() { return pricePerLitre; }
    public void setPricePerLitre(BigDecimal pricePerLitre) { this.pricePerLitre = pricePerLitre; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum Status { DELIVERED, PARTIAL, REJECTED }

}
