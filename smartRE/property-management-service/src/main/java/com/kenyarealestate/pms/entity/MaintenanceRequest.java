package com.kenyarealestate.pms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "maintenance_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "lease_id")
    private UUID leaseId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "landlord_id", nullable = false)
    private UUID landlordId;

    @Column(name = "raised_by")
    private UUID raisedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "raised_by_role", nullable = false, length = 16)
    private RaisedByRole raisedByRole = RaisedByRole.TENANT;

    @Column(nullable = false, length = 32)
    private String reference;

    @Column(nullable = false, length = 32)
    private String category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MaintenancePriority priority = MaintenancePriority.MEDIUM;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    @Column(name = "assigned_to", length = 160)
    private String assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(precision = 14, scale = 2)
    private BigDecimal cost;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** Who bears the cost. Null until resolved and a decision has been taken. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cost_borne_by", length = 16)
    private CostBearer costBorneBy;

    /** The tenant's portion when SHARED. Null for LANDLORD; equals cost for TENANT. */
    @Column(name = "tenant_charge", precision = 12, scale = 2)
    private BigDecimal tenantCharge;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
