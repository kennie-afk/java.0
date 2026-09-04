package com.kenyarealestate.pms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "units")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "landlord_id", nullable = false)
    private UUID landlordId;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(name = "unit_type", length = 32)
    private String unitType;

    private Integer bedrooms;
    private Integer bathrooms;

    @Column(name = "size_sqm", precision = 10, scale = 2)
    private BigDecimal sizeSqm;

    @Column(name = "rent_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "deposit_amount", precision = 14, scale = 2)
    private BigDecimal depositAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UnitStatus status = UnitStatus.VACANT;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
