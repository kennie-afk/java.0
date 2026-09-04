package com.kenyarealestate.pms.kafka;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.MaintenanceRequest;
import com.kenyarealestate.pms.entity.RentInvoice;
import com.kenyarealestate.pms.repository.UnitRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class PmsEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UnitRepository units;

    @Value("${kafka.topics.pms-events:pms-events}")
    private String topic;

    public PmsEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, UnitRepository units) {
        this.kafkaTemplate = kafkaTemplate;
        this.units = units;
    }

    public void publishLeaseActivated(Lease lease, UUID propertyId) {
        var event = Events.LeaseActivatedEvent.builder()
                .eventType("LEASE_ACTIVATED")
                .leaseId(lease.getId())
                .unitId(lease.getUnitId())
                .tenantId(lease.getTenantId())
                .landlordId(lease.getLandlordId())
                .propertyId(propertyId)
                .rentAmount(lease.getRentAmount())
                .billingDay(lease.getBillingDay())
                .startDate(lease.getStartDate())
                .activatedAt(LocalDateTime.now())
                .build();
        send(lease.getId().toString(), event, "LEASE_ACTIVATED");
    }

    public void publishLeaseEnded(Lease lease, String reason) {
        var event = Events.LeaseEndedEvent.builder()
                .eventType("LEASE_ENDED")
                .leaseId(lease.getId())
                .unitId(lease.getUnitId())
                .tenantId(lease.getTenantId())
                .landlordId(lease.getLandlordId())
                .reason(reason)
                .endedAt(LocalDateTime.now())
                .build();
        send(lease.getId().toString(), event, "LEASE_ENDED");
    }

    public void publishRentInvoiceIssued(RentInvoice invoice, UUID tenantUserId) {
        var event = Events.RentInvoiceIssuedEvent.builder()
                .eventType("RENT_INVOICE_ISSUED")
                .invoiceId(invoice.getId())
                .leaseId(invoice.getLeaseId())
                .tenantId(invoice.getTenantId())
                .tenantUserId(tenantUserId)
                .landlordId(invoice.getLandlordId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .unitLabel(unitLabel(invoice.getUnitId()))
                .amountDue(invoice.getAmountDue())
                .dueDate(invoice.getDueDate())
                .issuedAt(LocalDateTime.now())
                .build();
        send(invoice.getId().toString(), event, "RENT_INVOICE_ISSUED");
    }

    public void publishRentOverdue(RentInvoice invoice, int daysOverdue, UUID tenantUserId) {
        var event = Events.RentOverdueEvent.builder()
                .eventType("RENT_OVERDUE")
                .invoiceId(invoice.getId())
                .leaseId(invoice.getLeaseId())
                .tenantId(invoice.getTenantId())
                .tenantUserId(tenantUserId)
                .landlordId(invoice.getLandlordId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .unitLabel(unitLabel(invoice.getUnitId()))
                .balance(invoice.getBalance())
                .dueDate(invoice.getDueDate())
                .daysOverdue(daysOverdue)
                .detectedAt(LocalDateTime.now())
                .build();
        send(invoice.getId().toString() + ":" + daysOverdue, event, "RENT_OVERDUE");
    }

    public void publishRentReceived(RentInvoice invoice, java.math.BigDecimal amount, UUID tenantUserId) {
        var event = Events.RentReceivedEvent.builder()
                .eventType("RENT_RECEIVED")
                .invoiceId(invoice.getId())
                .leaseId(invoice.getLeaseId())
                .tenantId(invoice.getTenantId())
                .tenantUserId(tenantUserId)
                .landlordId(invoice.getLandlordId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .unitLabel(unitLabel(invoice.getUnitId()))
                .amount(amount)
                .balance(invoice.getBalance())
                .invoiceStatus(invoice.getStatus().name())
                .receivedAt(LocalDateTime.now())
                .build();
        send(invoice.getId().toString(), event, "RENT_RECEIVED");
    }

    public void publishMaintenanceRaised(MaintenanceRequest r, String unitLabel, UUID tenantUserId) {
        var event = Events.MaintenanceRaisedEvent.builder()
                .eventType("MAINTENANCE_RAISED")
                .requestId(r.getId())
                .unitId(r.getUnitId())
                .unitLabel(unitLabel)
                .tenantId(r.getTenantId())
                .tenantUserId(tenantUserId)
                .landlordId(r.getLandlordId())
                .reference(r.getReference())
                .category(r.getCategory())
                .priority(r.getPriority().name())
                .title(r.getTitle())
                .raisedByRole(r.getRaisedByRole().name())
                .raisedAt(LocalDateTime.now())
                .build();
        send(r.getId().toString(), event, "MAINTENANCE_RAISED");
    }

    public void publishMaintenanceResolved(MaintenanceRequest r, String unitLabel, UUID tenantUserId) {
        var event = Events.MaintenanceResolvedEvent.builder()
                .eventType("MAINTENANCE_RESOLVED")
                .requestId(r.getId())
                .unitId(r.getUnitId())
                .unitLabel(unitLabel)
                .tenantId(r.getTenantId())
                .tenantUserId(tenantUserId)
                .landlordId(r.getLandlordId())
                .reference(r.getReference())
                .title(r.getTitle())
                .status(r.getStatus().name())
                .resolutionNotes(r.getResolutionNotes())
                .resolvedAt(LocalDateTime.now())
                .build();
        send(r.getId().toString() + ":" + r.getStatus(), event, "MAINTENANCE_RESOLVED");
    }

    private String unitLabel(UUID unitId) {
        return units.findById(unitId).map(u -> u.getLabel()).orElse(null);
    }

    private void send(String key, Object event, String label) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        String traceId = MDC.get("correlationId");
        if (StringUtils.hasText(traceId)) {
            record.headers().add("X-Correlation-Id", traceId.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) log.error("Failed to publish {} for {}: {}", label, key, ex.getMessage());
            else log.info("Published {} for {}", label, key);
        });
    }
}
