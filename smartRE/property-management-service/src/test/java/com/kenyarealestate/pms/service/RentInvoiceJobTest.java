package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import com.kenyarealestate.pms.entity.RentInvoice;
import com.kenyarealestate.pms.repository.LeaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentInvoiceJobTest {

    @Mock
    private LeaseRepository leases;
    @Mock
    private RentInvoiceService invoiceService;

    private static Lease lease(UUID id) {
        Lease lease = new Lease();
        lease.setId(id);
        lease.setStatus(LeaseStatus.ACTIVE);
        return lease;
    }

    /**
     * A platform-wide scan must not stop after the first batch just because a batch
     * happens to be full - the bug this replaces loaded the whole result set in one
     * query, so there was nothing to test here before.
     */
    @Test
    void walksMultipleBatchesRatherThanStoppingAfterTheFirstFullPage() {
        RentInvoiceJob job = new RentInvoiceJob(leases, invoiceService);

        List<Lease> firstBatch = IntStream.range(0, 500)
            .mapToObj(i -> lease(UUID.randomUUID()))
            .collect(Collectors.toList());
        List<Lease> secondBatch = IntStream.range(0, 12)
            .mapToObj(i -> lease(UUID.randomUUID()))
            .collect(Collectors.toList());

        when(leases.findByStatusOrderByIdAsc(eq(LeaseStatus.ACTIVE), any(Pageable.class)))
            .thenReturn(firstBatch);
        when(leases.findByStatusAndIdGreaterThanOrderByIdAsc(
                eq(LeaseStatus.ACTIVE), any(UUID.class), any(Pageable.class)))
            .thenReturn(secondBatch);
        when(invoiceService.issueIfDue(any(Lease.class), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        job.run();

        verify(leases).findByStatusOrderByIdAsc(eq(LeaseStatus.ACTIVE), eq(PageRequest.of(0, 500)));
        verify(leases).findByStatusAndIdGreaterThanOrderByIdAsc(
            eq(LeaseStatus.ACTIVE), eq(firstBatch.get(firstBatch.size() - 1).getId()), any(Pageable.class));

        ArgumentCaptor<Lease> scanned = ArgumentCaptor.forClass(Lease.class);
        verify(invoiceService, org.mockito.Mockito.times(512)).issueIfDue(scanned.capture(), any(LocalDate.class));
        assertEquals(512, scanned.getAllValues().size());
    }

    @Test
    void stopsAfterAShortFinalBatchWithoutQueryingAgain() {
        RentInvoiceJob job = new RentInvoiceJob(leases, invoiceService);

        List<Lease> onlyBatch = List.of(lease(UUID.randomUUID()), lease(UUID.randomUUID()));
        when(leases.findByStatusOrderByIdAsc(eq(LeaseStatus.ACTIVE), any(Pageable.class)))
            .thenReturn(onlyBatch);
        when(invoiceService.issueIfDue(any(Lease.class), any(LocalDate.class)))
            .thenReturn(Optional.of(new RentInvoice()));

        job.run();

        verify(leases).findByStatusOrderByIdAsc(eq(LeaseStatus.ACTIVE), any(Pageable.class));
        verify(leases, org.mockito.Mockito.never())
            .findByStatusAndIdGreaterThanOrderByIdAsc(any(), any(), any());
    }

    @Test
    void aFailureOnOneLeaseDoesNotStopTheRestOfTheBatch() {
        RentInvoiceJob job = new RentInvoiceJob(leases, invoiceService);

        Lease broken = lease(UUID.randomUUID());
        Lease fine = lease(UUID.randomUUID());
        when(leases.findByStatusOrderByIdAsc(eq(LeaseStatus.ACTIVE), any(Pageable.class)))
            .thenReturn(List.of(broken, fine));
        when(invoiceService.issueIfDue(eq(broken), any(LocalDate.class)))
            .thenThrow(new RuntimeException("boom"));
        when(invoiceService.issueIfDue(eq(fine), any(LocalDate.class)))
            .thenReturn(Optional.of(new RentInvoice()));

        job.run();

        verify(invoiceService).issueIfDue(eq(fine), any(LocalDate.class));
    }
}
