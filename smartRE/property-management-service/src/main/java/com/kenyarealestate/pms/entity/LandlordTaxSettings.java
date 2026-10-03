package com.kenyarealestate.pms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A landlord's own monthly-rental-income-tax rate, when it differs from the platform default. */
@Entity
@Table(name = "landlord_tax_settings")
public class LandlordTaxSettings {

    @Id
    @Column(name = "landlord_id")
    private UUID landlordId;

    @Column(name = "mri_rate_percent", precision = 5, scale = 2)
    private BigDecimal mriRatePercent;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected LandlordTaxSettings() {
    }

    public LandlordTaxSettings(UUID landlordId, BigDecimal mriRatePercent) {
        this.landlordId = landlordId;
        this.mriRatePercent = mriRatePercent;
    }

    public UUID getLandlordId() { return landlordId; }
    public BigDecimal getMriRatePercent() { return mriRatePercent; }

    public void setMriRatePercent(BigDecimal rate) {
        this.mriRatePercent = rate;
        this.updatedAt = LocalDateTime.now();
    }
}
