package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.InvoiceResponse;
import com.kenyarealestate.pms.dto.RentInvoiceRefResponse;
import com.kenyarealestate.pms.entity.InvoiceStatus;
import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import com.kenyarealestate.pms.entity.PaymentFrequency;
import com.kenyarealestate.pms.entity.RentInvoice;
import com.kenyarealestate.pms.entity.Tenant;
import com.kenyarealestate.pms.entity.Unit;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.RentInvoiceRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import com.kenyarealestate.pms.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentInvoiceServiceTest {

    private static final int GRACE_DAYS = 5;
    private static final int LEAD_DAYS = 7;

    @Mock private RentInvoiceRepository invoices;
    @Mock private LeaseRepository leases;
    @Mock private TenantRepository tenants;
    @Mock private UnitRepository units;
    @Mock private PmsEventPublisher publisher;

    private RentInvoiceService service;

    private UUID landlordId;
    private UUID tenantId;
    private UUID unitId;
    private Unit unit;
    private Tenant tenant;
    private Lease lease;

    @BeforeEach
    void setup() {
        service = new RentInvoiceService(invoices, leases, tenants, units, publisher, GRACE_DAYS, LEAD_DAYS);

        landlordId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        unit = Unit.builder()
                .id(unitId)
                .propertyId(UUID.randomUUID())
                .landlordId(landlordId)
                .label("Flat B-12")
                .build();
        tenant = Tenant.builder()
                .id(tenantId)
                .landlordId(landlordId)
                .userId(UUID.randomUUID())
                .fullName("Demo Tenant")
                .phone("254700000000")
                .build();
        lease = Lease.builder()
                .id(UUID.randomUUID())
                .unitId(unitId)
                .tenantId(tenantId)
                .landlordId(landlordId)
                .startDate(LocalDate.of(2026, 1, 1))
                .rentAmount(BigDecimal.valueOf(45000))
                .billingDay(1)
                .paymentFrequency(PaymentFrequency.MONTHLY)
                .status(LeaseStatus.ACTIVE)
                .build();

        when(units.findById(unitId)).thenReturn(Optional.of(unit));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(invoices.saveAndFlush(any(RentInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private RentInvoice invoice(InvoiceStatus status, BigDecimal amountDue, BigDecimal amountPaid) {
        return RentInvoice.builder()
                .id(UUID.randomUUID())
                .leaseId(lease.getId())
                .unitId(unitId)
                .tenantId(tenantId)
                .landlordId(landlordId)
                .invoiceNumber("RNT-202601-FLATB1-ABCD")
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 1, 31))
                .dueDate(LocalDate.of(2026, 1, 6))
                .amountDue(amountDue)
                .amountPaid(amountPaid)
                .status(status)
                .build();
    }

    @ParameterizedTest
    @EnumSource(value = LeaseStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void issueIfDue_billsNothingForALeaseThatIsNotActive(LeaseStatus status) {
        lease.setStatus(status);

        assertTrue(service.issueIfDue(lease, LocalDate.of(2026, 1, 1)).isEmpty());

        verify(invoices, never()).saveAndFlush(any(RentInvoice.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void issueIfDue_issuesTheFirstPeriodAndPublishesIt() {
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        Optional<RentInvoice> issued = service.issueIfDue(lease, LocalDate.of(2026, 1, 1));

        assertTrue(issued.isPresent());
        assertEquals(LocalDate.of(2026, 1, 1), issued.get().getPeriodStart());
        assertEquals(LocalDate.of(2026, 1, 31), issued.get().getPeriodEnd());
        assertEquals(LocalDate.of(2026, 1, 6), issued.get().getDueDate());
        assertEquals(BigDecimal.valueOf(45000), issued.get().getAmountDue());
        verify(publisher).publishRentInvoiceIssued(any(RentInvoice.class), any());
    }

    @Test
    void issueIfDue_neverBillsTheSamePeriodTwice() {
        when(invoices.findByLeaseIdAndPeriodStart(lease.getId(), LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.of(invoice(InvoiceStatus.PENDING, BigDecimal.valueOf(45000), BigDecimal.ZERO)));
        when(invoices.findByLeaseIdAndPeriodStart(lease.getId(), LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.empty());

        service.issueIfDue(lease, LocalDate.of(2026, 1, 1));

        verify(invoices, never()).saveAndFlush(any(RentInvoice.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void issueIfDue_billsAheadOnlyAsFarAsTheLeadWindow() {
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        service.issueIfDue(lease, LocalDate.of(2026, 1, 28));

        verify(invoices, org.mockito.Mockito.times(2)).saveAndFlush(any(RentInvoice.class));
    }

    @Test
    void issueIfDue_stopsBillingAfterTheLeaseEnds() {
        lease.setEndDate(LocalDate.of(2026, 1, 31));
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        service.issueIfDue(lease, LocalDate.of(2026, 1, 28));

        verify(invoices, org.mockito.Mockito.times(1)).saveAndFlush(any(RentInvoice.class));
    }

    @Test
    void issueIfDue_swallowsTheRaceWhenAnotherWorkerBilledTheSamePeriod() {
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());
        when(invoices.saveAndFlush(any(RentInvoice.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertTrue(service.issueIfDue(lease, LocalDate.of(2026, 1, 1)).isEmpty());

        verifyNoInteractions(publisher);
    }

    @Test
    void issueIfDue_scalesTheChargeToTheBillingFrequency() {
        lease.setPaymentFrequency(PaymentFrequency.QUARTERLY);
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        Optional<RentInvoice> issued = service.issueIfDue(lease, LocalDate.of(2026, 1, 1));

        assertTrue(issued.isPresent());
        assertEquals(BigDecimal.valueOf(135000), issued.get().getAmountDue());
        assertEquals(LocalDate.of(2026, 3, 31), issued.get().getPeriodEnd());
    }

    @Test
    void issueIfDue_buildsAReadableInvoiceNumberFromTheUnitAndPeriod() {
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        Optional<RentInvoice> issued = service.issueIfDue(lease, LocalDate.of(2026, 1, 1));

        assertTrue(issued.isPresent());
        String number = issued.get().getInvoiceNumber();
        assertTrue(number.startsWith("RNT-202601-FLATB1-"), number);
        assertEquals(unitId.toString().substring(0, 4).toUpperCase(), number.substring(number.length() - 4));
    }

    @Test
    void issueIfDue_fallsBackWhenTheUnitLabelHasNoUsableCharacters() {
        unit.setLabel("- / -");
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());

        Optional<RentInvoice> issued = service.issueIfDue(lease, LocalDate.of(2026, 1, 1));

        assertTrue(issued.isPresent());
        assertTrue(issued.get().getInvoiceNumber().contains("-UNIT-"), issued.get().getInvoiceNumber());
    }

    @Test
    void issueIfDue_failsLoudlyWhenTheLeasedUnitIsMissing() {
        when(invoices.findByLeaseIdAndPeriodStart(any(), any())).thenReturn(Optional.empty());
        when(units.findById(unitId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.issueIfDue(lease, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void resolveByReference_normalisesTheTypedPaybillAccountNumber() {
        RentInvoice inv = invoice(InvoiceStatus.PENDING, BigDecimal.valueOf(45000), BigDecimal.ZERO);
        when(invoices.findByInvoiceNumber("RNT-202601-FLATB1-ABCD")).thenReturn(Optional.of(inv));

        RentInvoiceRefResponse ref = service.resolveByReference("  rnt-202601-flatb1-abcd  ");

        assertEquals(inv.getId(), ref.getInvoiceId());
        assertEquals(unit.getPropertyId(), ref.getPropertyId());
        assertEquals(tenant.getUserId(), ref.getTenantUserId());
        assertEquals(BigDecimal.valueOf(45000), ref.getBalance());
    }

    @Test
    void resolveByReference_rejectsAnUnknownAccountNumber() {
        when(invoices.findByInvoiceNumber(any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.resolveByReference("RNT-NOPE"));
    }

    @Test
    void requireOwned_refusesAnInvoiceBelongingToAnotherLandlord() {
        RentInvoice inv = invoice(InvoiceStatus.PENDING, BigDecimal.valueOf(45000), BigDecimal.ZERO);
        when(invoices.findById(inv.getId())).thenReturn(Optional.of(inv));

        assertThrows(ForbiddenException.class, () -> service.requireOwned(UUID.randomUUID(), inv.getId()));
    }

    @Test
    void writeOff_marksAnUnpaidInvoiceAsWrittenOff() {
        RentInvoice inv = invoice(InvoiceStatus.OVERDUE, BigDecimal.valueOf(45000), BigDecimal.ZERO);
        when(invoices.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(invoices.save(inv)).thenReturn(inv);

        InvoiceResponse response = service.writeOff(landlordId, inv.getId());

        assertEquals(InvoiceStatus.WRITTEN_OFF.name(), response.getStatus());
    }

    @Test
    void writeOff_refusesAnInvoiceThatIsAlreadySettled() {
        RentInvoice inv = invoice(InvoiceStatus.PAID, BigDecimal.valueOf(45000), BigDecimal.valueOf(45000));
        when(invoices.findById(inv.getId())).thenReturn(Optional.of(inv));

        assertThrows(ConflictException.class, () -> service.writeOff(landlordId, inv.getId()));

        verify(invoices, never()).save(any(RentInvoice.class));
    }

    @Test
    void listForLease_refusesALeaseBelongingToAnotherLandlord() {
        when(leases.findById(lease.getId())).thenReturn(Optional.of(lease));

        assertThrows(ForbiddenException.class,
                () -> service.listForLease(UUID.randomUUID(), lease.getId()));
    }

    @Test
    void listForTenantUser_returnsNothingWhenTheUserIsNobodysTenant() {
        when(tenants.findAll()).thenReturn(List.of(tenant));

        assertTrue(service.listForTenantUser(UUID.randomUUID(),
                org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());

        verify(invoices, never()).findByTenantIdInOrderByDueDateDesc(any(), any());
    }

    @Test
    void outstandingFor_reportsZeroRatherThanNullWhenNothingIsOwed() {
        when(invoices.outstandingFor(landlordId)).thenReturn(null);

        assertEquals(BigDecimal.ZERO, service.outstandingFor(landlordId));
    }

    @Test
    void listForLandlord_rejectsAnUnknownStatusFilterWithGuidance() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.listForLandlord(landlordId, "SETTLED",
                        org.springframework.data.domain.PageRequest.of(0, 20)));

        assertTrue(ex.getMessage().contains("PENDING"));
    }

    @Test
    void toResponse_carriesTheUnitAndTenantLabelsAndTheRunningBalance() {
        RentInvoice inv = invoice(InvoiceStatus.PARTIAL, BigDecimal.valueOf(45000), BigDecimal.valueOf(20000));

        InvoiceResponse response = service.toResponse(inv);

        assertEquals("Flat B-12", response.getUnitLabel());
        assertEquals("Demo Tenant", response.getTenantName());
        assertEquals("254700000000", response.getTenantPhone());
        assertEquals(0, BigDecimal.valueOf(25000).compareTo(response.getBalance()));
        assertNotNull(response.getInvoiceNumber());
    }

    @Nested
    @DisplayName("who may look at an invoice")
    class Visibility {

        private final UUID landlord = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private final UUID tenantUser = UUID.randomUUID();
        private final UUID invoiceId = UUID.randomUUID();

        private RentInvoice theInvoice() {
            return RentInvoice.builder()
                    .id(invoiceId).leaseId(UUID.randomUUID()).unitId(UUID.randomUUID())
                    .tenantId(tenantId).landlordId(landlord).invoiceNumber("RNT-202610-B1-0001")
                    .periodStart(LocalDate.of(2026, 10, 5)).periodEnd(LocalDate.of(2026, 11, 4))
                    .dueDate(LocalDate.of(2026, 10, 10))
                    .amountDue(new BigDecimal("120000")).amountPaid(BigDecimal.ZERO)
                    .status(InvoiceStatus.PENDING).build();
        }

        @BeforeEach
        void invoiceExists() {
            when(invoices.findById(invoiceId)).thenReturn(Optional.of(theInvoice()));
        }

        @Test
        @DisplayName("the landlord who issued it")
        void landlordMaySee() {
            assertEquals(invoiceId, service.requireVisibleTo(landlord, invoiceId).getId());
        }

        @Test
        @DisplayName("the tenant it is addressed to — without this they cannot see a receipt for money they paid")
        void linkedTenantMaySee() {
            when(tenants.findByUserId(tenantUser)).thenReturn(List.of(
                    Tenant.builder().id(tenantId).landlordId(landlord).userId(tenantUser)
                            .fullName("David Kimani").phone("254733444555").build()));

            assertEquals(invoiceId, service.requireVisibleTo(tenantUser, invoiceId).getId());
        }

        @Test
        @DisplayName("nobody else — a stranger with a valid account is refused")
        void strangerRefused() {
            UUID stranger = UUID.randomUUID();
            when(tenants.findByUserId(stranger)).thenReturn(List.of());

            assertThrows(ForbiddenException.class, () -> service.requireVisibleTo(stranger, invoiceId));
        }

        @Test
        @DisplayName("another landlord's tenant is refused, even though they are somebody's tenant")
        void otherTenancyRefused() {
            UUID otherUser = UUID.randomUUID();
            when(tenants.findByUserId(otherUser)).thenReturn(List.of(
                    Tenant.builder().id(UUID.randomUUID()).landlordId(UUID.randomUUID()).userId(otherUser)
                            .fullName("Someone Else").phone("254700000000").build()));

            assertThrows(ForbiddenException.class, () -> service.requireVisibleTo(otherUser, invoiceId));
        }

        @Test
        @DisplayName("visibility is read-only — it does not make a tenant the owner")
        void visibilityIsNotOwnership() {
            when(tenants.findByUserId(tenantUser)).thenReturn(List.of(
                    Tenant.builder().id(tenantId).landlordId(landlord).userId(tenantUser)
                            .fullName("David Kimani").phone("254733444555").build()));

            assertThrows(ForbiddenException.class, () -> service.requireOwned(tenantUser, invoiceId));
        }
    }
}
