package com.kenyarealestate.pms.kafka;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Events {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LeaseActivatedEvent {
        private String eventType;
        private UUID leaseId;
        private UUID unitId;
        private UUID tenantId;
        private UUID landlordId;
        private UUID propertyId;
        private BigDecimal rentAmount;
        private Integer billingDay;
        private LocalDate startDate;
        private LocalDateTime activatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RentInvoiceIssuedEvent {
        private String eventType;
        private UUID invoiceId;
        private UUID leaseId;
        private UUID tenantId;
        private UUID tenantUserId;
        private UUID landlordId;
        private String invoiceNumber;
        private String unitLabel;
        private BigDecimal amountDue;
        private LocalDate dueDate;
        private LocalDateTime issuedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RentOverdueEvent {
        private String eventType;
        private UUID invoiceId;
        private UUID leaseId;
        private UUID tenantId;
        private UUID tenantUserId;
        private UUID landlordId;
        private String invoiceNumber;
        private String unitLabel;
        private BigDecimal balance;
        private LocalDate dueDate;
        private int daysOverdue;
        private LocalDateTime detectedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RentReceivedEvent {
        private String eventType;
        private UUID invoiceId;
        private UUID leaseId;
        private UUID tenantId;
        private UUID tenantUserId;
        private UUID landlordId;
        private String invoiceNumber;
        private String unitLabel;
        private BigDecimal amount;
        private BigDecimal balance;
        private String invoiceStatus;
        private LocalDateTime receivedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MaintenanceRaisedEvent {
        private String eventType;
        private UUID requestId;
        private UUID unitId;
        private String unitLabel;
        private UUID tenantId;
        private UUID tenantUserId;
        private UUID landlordId;
        private String reference;
        private String category;
        private String priority;
        private String title;
        private String raisedByRole;
        private LocalDateTime raisedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MaintenanceResolvedEvent {
        private String eventType;
        private UUID requestId;
        private UUID unitId;
        private String unitLabel;
        private UUID tenantId;
        private UUID tenantUserId;
        private UUID landlordId;
        private String reference;
        private String title;
        private String status;
        private String resolutionNotes;
        private LocalDateTime resolvedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LeaseEndedEvent {
        private String eventType;
        private UUID leaseId;
        private UUID unitId;
        private UUID tenantId;
        private UUID landlordId;
        private String reason;
        private LocalDateTime endedAt;
    }
}
