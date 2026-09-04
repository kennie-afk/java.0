package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.InvoiceStatus;
import com.kenyarealestate.pms.entity.RentInvoice;
import com.kenyarealestate.pms.entity.Tenant;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.RentInvoiceRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RentArrearsJob {

    private static final int[] REMINDER_DAYS = { 1, 7, 14 };
    private static final int BATCH_SIZE = 200;

    private final RentInvoiceRepository invoices;
    private final TenantRepository tenants;
    private final PmsEventPublisher publisher;

    public RentArrearsJob(RentInvoiceRepository invoices, TenantRepository tenants, PmsEventPublisher publisher) {
        this.invoices = invoices;
        this.tenants = tenants;
        this.publisher = publisher;
    }

    @Scheduled(cron = "${pms.arrears.cron:0 30 2 * * *}")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now();
        List<RentInvoice> chaseable = invoices.findChaseable(today, PageRequest.of(0, BATCH_SIZE));
        if (chaseable.isEmpty()) return;

        int flagged = 0, reminded = 0;
        for (RentInvoice invoice : chaseable) {
            try {
                if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
                    invoice.setStatus(InvoiceStatus.OVERDUE);
                    flagged++;
                }

                int daysOverdue = (int) ChronoUnit.DAYS.between(invoice.getDueDate(), today);
                Integer milestone = dueMilestone(daysOverdue, invoice.getLastReminderDay());
                if (milestone != null) {
                    invoice.setLastReminderDay(milestone);
                    publisher.publishRentOverdue(invoice, milestone, tenantUserId(invoice.getTenantId()));
                    reminded++;
                }
                invoices.save(invoice);
            } catch (Exception e) {
                log.error("Arrears run failed for invoice {}: {}", invoice.getId(), e.getMessage(), e);
            }
        }
        log.info("Arrears run: {} scanned, {} newly overdue, {} reminder(s) sent",
                chaseable.size(), flagged, reminded);
    }

    private Integer dueMilestone(int daysOverdue, Integer lastSent) {
        int alreadySent = lastSent == null ? 0 : lastSent;
        Integer due = null;
        for (int day : REMINDER_DAYS) {
            if (daysOverdue >= day && day > alreadySent) due = day;
        }
        return due;
    }

    private UUID tenantUserId(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getUserId).orElse(null);
    }
}
