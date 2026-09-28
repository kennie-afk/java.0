package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import com.kenyarealestate.pms.repository.LeaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RentInvoiceJob {

    private static final int BATCH_SIZE = 500;

    private final LeaseRepository leases;
    private final RentInvoiceService invoiceService;

    public RentInvoiceJob(LeaseRepository leases, RentInvoiceService invoiceService) {
        this.leases = leases;
        this.invoiceService = invoiceService;
    }

    @Scheduled(cron = "${pms.invoice.cron:0 0 2 * * *}")
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("Rent invoice run starting for {}", today);

        // Walked in batches ordered by id rather than loaded as one list: every ACTIVE lease
        // platform-wide in a single query is an unbounded result set and an unbounded heap,
        // and does not survive growth past a few hundred thousand leases.
        int scanned = 0;
        int issued = 0;
        UUID lastId = null;
        List<Lease> batch;
        do {
            batch = lastId == null
                ? leases.findByStatusOrderByIdAsc(LeaseStatus.ACTIVE, PageRequest.of(0, BATCH_SIZE))
                : leases.findByStatusAndIdGreaterThanOrderByIdAsc(LeaseStatus.ACTIVE, lastId, PageRequest.of(0, BATCH_SIZE));

            for (Lease lease : batch) {
                scanned++;
                try {
                    if (invoiceService.issueIfDue(lease, today).isPresent()) issued++;
                } catch (Exception e) {
                    log.error("Invoice generation failed for lease {}: {}", lease.getId(), e.getMessage(), e);
                }
                lastId = lease.getId();
            }
        } while (batch.size() == BATCH_SIZE);

        log.info("Rent invoice run finished: {} lease(s) scanned, {} invoice(s) issued", scanned, issued);
    }
}
