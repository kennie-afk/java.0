package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.LeaseResponse;
import com.kenyarealestate.pms.dto.RentPaymentResponse;
import com.kenyarealestate.pms.dto.UnitResponse;
import com.kenyarealestate.pms.entity.PaymentMethod;
import com.kenyarealestate.pms.entity.RentPayment;
import com.kenyarealestate.pms.entity.RentPaymentStatus;
import com.kenyarealestate.pms.entity.Unit;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.RentPaymentRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import com.kenyarealestate.pms.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The queries behind the three lists that used to be unbounded. */
class PagedServiceQueriesTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID PROPERTY = UUID.randomUUID();

    @Test
    void unitsOnAPropertyAreFilteredToTheLandlordInTheQueryNotAfterwards() {
        UnitRepository repo = mock(UnitRepository.class);
        UnitService service = new UnitService(repo, mock(LeaseRepository.class), mock(TenantRepository.class),
                null, mock(RentInvoiceService.class), null);
        Unit mine = Unit.builder().id(UUID.randomUUID()).propertyId(PROPERTY).landlordId(LANDLORD).label("A1")
                .rentAmount(BigDecimal.TEN).build();
        when(repo.findByPropertyIdAndLandlordId(eq(PROPERTY), eq(LANDLORD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mine)));

        Page<UnitResponse> page = service.listByProperty(LANDLORD, PROPERTY, PageRequest.of(1, 50));

        assertThat(page.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> sent = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByPropertyIdAndLandlordId(eq(PROPERTY), eq(LANDLORD), sent.capture());
        assertThat(sent.getValue().getPageNumber()).isEqualTo(1);
        assertThat(sent.getValue().getPageSize()).isEqualTo(50);
        // Stable order: label, then id as the tiebreak, so a page boundary never repeats or drops a unit.
        assertThat(sent.getValue().getSort().toString()).isEqualTo("label: ASC,id: ASC");
        verify(repo, never()).findByPropertyIdOrderByLabelAsc(any());
    }

    @Test
    void paymentsOnAnInvoiceArePagedNewestFirstWithAnIdTiebreak() {
        RentPaymentRepository payments = mock(RentPaymentRepository.class);
        RentInvoiceService invoiceService = mock(RentInvoiceService.class);
        RentPaymentService service = new RentPaymentService(null, payments, null, null, null, null,
                invoiceService, null);
        UUID invoiceId = UUID.randomUUID();
        RentPayment payment = RentPayment.builder().id(UUID.randomUUID()).invoiceId(invoiceId)
                .amount(BigDecimal.valueOf(5000)).method(PaymentMethod.CASH).status(RentPaymentStatus.CONFIRMED).build();
        when(payments.findByInvoiceId(eq(invoiceId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment)));

        Page<RentPaymentResponse> page = service.forInvoice(LANDLORD, invoiceId, PageRequest.of(0, 200));

        assertThat(page.getContent()).singleElement().extracting(RentPaymentResponse::getAmount)
                .isEqualTo(BigDecimal.valueOf(5000));
        verify(invoiceService).requireVisibleTo(LANDLORD, invoiceId);
        ArgumentCaptor<Pageable> sent = ArgumentCaptor.forClass(Pageable.class);
        verify(payments).findByInvoiceId(eq(invoiceId), sent.capture());
        assertThat(sent.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: ASC");
    }

    @Test
    void aLeaseHistoryChecksOwnershipFirstThenPages() {
        LeaseRepository leases = mock(LeaseRepository.class);
        UnitService unitService = mock(UnitService.class);
        LeaseService service = new LeaseService(leases, null, null, unitService, null, null,
                BigDecimal.TEN, 30);
        UUID unitId = UUID.randomUUID();
        when(leases.findByUnitId(eq(unitId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        Page<LeaseResponse> page = service.historyForUnit(LANDLORD, unitId, PageRequest.of(0, 25));

        assertThat(page.getContent()).isEmpty();
        verify(unitService).ownedUnit(LANDLORD, unitId);
        ArgumentCaptor<Pageable> sent = ArgumentCaptor.forClass(Pageable.class);
        verify(leases).findByUnitId(eq(unitId), sent.capture());
        assertThat(sent.getValue().getSort().toString()).isEqualTo("startDate: DESC,id: ASC");
    }
}
