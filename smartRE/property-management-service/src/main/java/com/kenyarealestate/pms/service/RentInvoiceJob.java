package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import com.kenyarealestate.pms.repository.LeaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class RentInvoiceJob {

    private final LeaseRepository leases;
    private final RentInvoiceService invoiceService;

    public RentInvoiceJob(LeaseRepository leases, RentInvoiceService invoiceService) {
        this.leases = leases;
        this.invoiceService = invoiceService;
    }

    @Scheduled(cron = "${pms.invoice.cron:0 0 2 * * *}")
    public void run() {
        LocalDate today = LocalDate.now();
        List<Lease> active = leases.findByStatus(LeaseStatus.ACTIVE);
        log.info("Rent invoice run starting for {}", today);
        int issued = 0;
        for (Lease lease : active) {
            try {
                if (invoiceService.issueIfDue(lease, today).isPresent()) issued++;
            } catch (Exception e) {
                log.error("Invoice generation failed for lease {}: {}", lease.getId(), e.getMessage(), e);
            }
        }
        log.info("Rent invoice run finished: {} lease(s) scanned, {} invoice(s) issued", active.size(), issued);
    }
}
