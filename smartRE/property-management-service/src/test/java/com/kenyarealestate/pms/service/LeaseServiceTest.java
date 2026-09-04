package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.CreateLeaseRequest;
import com.kenyarealestate.pms.dto.RenewLeaseRequest;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import com.kenyarealestate.pms.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaseServiceTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID OTHER    = UUID.randomUUID();
    private static final UUID UNIT_ID  = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY = UUID.randomUUID();

    private LeaseRepository leases;
    private UnitRepository units;
    private TenantRepository tenants;
    private UnitService unitService;
    private TenantService tenantService;
    private PmsEventPublisher publisher;
    private LeaseService service;

    private Unit unit;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        leases = mock(LeaseRepository.class);
        units = mock(UnitRepository.class);
        tenants = mock(TenantRepository.class);
        unitService = mock(UnitService.class);
        tenantService = mock(TenantService.class);
        publisher = mock(PmsEventPublisher.class);

        unit = Unit.builder().id(UNIT_ID).propertyId(PROPERTY).landlordId(LANDLORD)
                .label("A3").rentAmount(new BigDecimal("35000"))
                .depositAmount(new BigDecimal("70000")).status(UnitStatus.VACANT).build();
        tenant = Tenant.builder().id(TENANT_ID).landlordId(LANDLORD).fullName("Achieng Otieno")
                .phone("254700111222").build();

        when(unitService.ownedUnit(LANDLORD, UNIT_ID)).thenReturn(unit);
        when(tenantService.owned(LANDLORD, TENANT_ID)).thenReturn(tenant);
        when(units.findById(UNIT_ID)).thenReturn(Optional.of(unit));
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(leases.save(any())).thenAnswer(i -> i.getArgument(0, Lease.class));
        when(leases.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0, Lease.class));

        service = new LeaseService(leases, units, tenants, unitService, tenantService, publisher,
                new BigDecimal("10.0"), 30);
    }

    private CreateLeaseRequest request() {
        return CreateLeaseRequest.builder()
                .unitId(UNIT_ID).tenantId(TENANT_ID)
                .startDate(LocalDate.of(2026, 10, 5))
                .build();
    }

    private Lease activeLease() {
        return Lease.builder().id(UUID.randomUUID()).unitId(UNIT_ID).tenantId(TENANT_ID)
                .landlordId(LANDLORD).startDate(LocalDate.of(2026, 1, 1))
                .rentAmount(new BigDecimal("35000")).depositAmount(BigDecimal.ZERO)
                .depositHeld(BigDecimal.ZERO).managementFeePct(new BigDecimal("10.0"))
                .billingDay(1).paymentFrequency(PaymentFrequency.MONTHLY)
                .noticePeriodDays(30).status(LeaseStatus.ACTIVE).build();
    }

    @Test
    void aNewLeaseStartsAsADraftAndDoesNotOccupyTheUnit() {
        var res = service.create(LANDLORD, request());
        assertEquals("DRAFT", res.getStatus());
        assertEquals(UnitStatus.VACANT, unit.getStatus());
        verify(publisher, never()).publishLeaseActivated(any(), any());
    }

    @Test
    void rentAndDepositFallBackToTheUnitWhenNotGiven() {
        var res = service.create(LANDLORD, request());
        assertEquals(0, new BigDecimal("35000").compareTo(res.getRentAmount()));
        assertEquals(0, new BigDecimal("70000").compareTo(res.getDepositAmount()));
    }

    @Test
    void theManagementFeeIsCapturedOntoTheLeaseAtDraftTime() {
        var res = service.create(LANDLORD, request());
        assertEquals(0, new BigDecimal("10.0").compareTo(res.getManagementFeePct()),
                "a later change to the platform default must not rewrite a signed lease");
    }

    @Test
    void anExplicitManagementFeeOverridesTheDefault() {
        var req = request();
        req.setManagementFeePct(new BigDecimal("7.5"));
        assertEquals(0, new BigDecimal("7.5").compareTo(service.create(LANDLORD, req).getManagementFeePct()));
    }

    @Test
    void theBillingDayFollowsTheStartDate() {
        assertEquals(5, service.create(LANDLORD, request()).getBillingDay());
    }

    @Test
    void aStartDateLateInTheMonthFallsBackToTheFirst() {
        var req = request();
        req.setStartDate(LocalDate.of(2026, 10, 31));
        assertEquals(1, service.create(LANDLORD, req).getBillingDay(),
                "not every month has a 31st, so the billing day must stay within 1-28");
    }

    @Test
    void anEndDateBeforeTheStartIsRejected() {
        var req = request();
        req.setEndDate(LocalDate.of(2026, 9, 1));
        assertThrows(ConflictException.class, () -> service.create(LANDLORD, req));
    }

    @Test
    void activatingALeaseOccupiesTheUnitAndAnnouncesIt() {
        Lease draft = Lease.builder().id(UUID.randomUUID()).unitId(UNIT_ID).tenantId(TENANT_ID)
                .landlordId(LANDLORD).startDate(LocalDate.of(2026, 10, 1))
                .rentAmount(new BigDecimal("35000")).status(LeaseStatus.DRAFT).build();
        when(leases.findById(draft.getId())).thenReturn(Optional.of(draft));

        var res = service.activate(LANDLORD, draft.getId());

        assertEquals("ACTIVE", res.getStatus());
        assertEquals(UnitStatus.OCCUPIED, unit.getStatus());
        verify(publisher).publishLeaseActivated(any(), eq(PROPERTY));
    }

    @Test
    void aSecondActiveLeaseOnOneUnitIsRefused() {
        Lease draft = Lease.builder().id(UUID.randomUUID()).unitId(UNIT_ID).tenantId(TENANT_ID)
                .landlordId(LANDLORD).startDate(LocalDate.of(2026, 10, 1))
                .rentAmount(BigDecimal.TEN).status(LeaseStatus.DRAFT).build();
        when(leases.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(leases.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_lease_one_active_per_unit"));

        ConflictException e = assertThrows(ConflictException.class, () -> service.activate(LANDLORD, draft.getId()));
        assertTrue(e.getMessage().contains("already has an active lease"));
    }

    @Test
    void onlyADraftCanBeActivated() {
        Lease active = activeLease();
        when(leases.findById(active.getId())).thenReturn(Optional.of(active));
        assertThrows(ConflictException.class, () -> service.activate(LANDLORD, active.getId()));
    }

    @Test
    void endingALeaseFreesTheUnit() {
        Lease active = activeLease();
        unit.setStatus(UnitStatus.OCCUPIED);
        when(leases.findById(active.getId())).thenReturn(Optional.of(active));

        var res = service.end(LANDLORD, active.getId());

        assertEquals("ENDED", res.getStatus());
        assertEquals(UnitStatus.VACANT, unit.getStatus());
        verify(publisher).publishLeaseEnded(any(), any());
    }

    @Test
    void terminatingRecordsTheReasonAndFreesTheUnit() {
        Lease active = activeLease();
        unit.setStatus(UnitStatus.OCCUPIED);
        when(leases.findById(active.getId())).thenReturn(Optional.of(active));

        var res = service.terminate(LANDLORD, active.getId(), "Tenant relocated to Kisumu");

        assertEquals("TERMINATED", res.getStatus());
        assertEquals("Tenant relocated to Kisumu", res.getTerminatedReason());
        assertNotNull(res.getTerminatedAt());
        assertEquals(UnitStatus.VACANT, unit.getStatus());
    }

    @Test
    void terminatingADraftDoesNotTouchTheUnitOrAnnounceAnything() {
        Lease draft = Lease.builder().id(UUID.randomUUID()).unitId(UNIT_ID).tenantId(TENANT_ID)
                .landlordId(LANDLORD).startDate(LocalDate.of(2026, 10, 1))
                .rentAmount(BigDecimal.TEN).status(LeaseStatus.DRAFT).build();
        when(leases.findById(draft.getId())).thenReturn(Optional.of(draft));

        service.terminate(LANDLORD, draft.getId(), "Signed elsewhere");

        assertEquals(UnitStatus.VACANT, unit.getStatus());
        verify(publisher, never()).publishLeaseEnded(any(), any());
    }

    @Test
    void renewalCarriesTermsForwardAndStartsAsADraft() {
        Lease active = activeLease();
        unit.setStatus(UnitStatus.OCCUPIED);
        when(leases.findById(active.getId())).thenReturn(Optional.of(active));

        var res = service.renew(LANDLORD, active.getId(), RenewLeaseRequest.builder()
                .startDate(LocalDate.of(2027, 1, 1))
                .rentAmount(new BigDecimal("38000"))
                .build());

        assertEquals("DRAFT", res.getStatus());
        assertEquals(0, new BigDecimal("38000").compareTo(res.getRentAmount()));
        assertEquals(0, new BigDecimal("10.0").compareTo(res.getManagementFeePct()));
        assertEquals(LeaseStatus.RENEWED, active.getStatus());
    }

    @Test
    void anotherLandlordsLeaseIsNotReachable() {
        Lease active = activeLease();
        when(leases.findById(active.getId())).thenReturn(Optional.of(active));
        assertThrows(ForbiddenException.class, () -> service.get(OTHER, active.getId()));
    }

    @Test
    void theActivationEventCarriesTheBillingDayThatRentWillRunOn() {
        Lease draft = Lease.builder().id(UUID.randomUUID()).unitId(UNIT_ID).tenantId(TENANT_ID)
                .landlordId(LANDLORD).startDate(LocalDate.of(2026, 10, 1))
                .rentAmount(new BigDecimal("35000")).billingDay(12).status(LeaseStatus.DRAFT).build();
        when(leases.findById(draft.getId())).thenReturn(Optional.of(draft));

        service.activate(LANDLORD, draft.getId());

        ArgumentCaptor<Lease> captor = ArgumentCaptor.forClass(Lease.class);
        verify(publisher).publishLeaseActivated(captor.capture(), eq(PROPERTY));
        assertEquals(12, captor.getValue().getBillingDay());
    }
}
