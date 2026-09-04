package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.dto.RaiseMaintenanceRequest;
import com.kenyarealestate.pms.dto.UpdateMaintenanceRequest;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaintenanceServiceTest {

    private static final UUID LANDLORD    = UUID.randomUUID();
    private static final UUID OTHER       = UUID.randomUUID();
    private static final UUID UNIT_ID     = UUID.randomUUID();
    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID TENANT_USER = UUID.randomUUID();

    private MaintenanceRequestRepository requests;
    private UnitRepository units;
    private TenantRepository tenants;
    private LeaseRepository leases;
    private PmsEventPublisher publisher;
    private MaintenanceService service;

    private Unit unit;
    private Lease lease;

    @BeforeEach
    void setUp() {
        requests = mock(MaintenanceRequestRepository.class);
        units = mock(UnitRepository.class);
        tenants = mock(TenantRepository.class);
        leases = mock(LeaseRepository.class);
        publisher = mock(PmsEventPublisher.class);

        unit = Unit.builder().id(UNIT_ID).landlordId(LANDLORD).propertyId(UUID.randomUUID())
                .label("B7").rentAmount(new BigDecimal("18000")).status(UnitStatus.OCCUPIED).build();
        lease = Lease.builder().id(UUID.randomUUID()).unitId(UNIT_ID).tenantId(TENANT_ID)
                .landlordId(LANDLORD).startDate(LocalDate.of(2026, 1, 1))
                .rentAmount(new BigDecimal("18000")).status(LeaseStatus.ACTIVE).build();

        when(units.findById(UNIT_ID)).thenReturn(Optional.of(unit));
        when(leases.findByStatus(LeaseStatus.ACTIVE)).thenReturn(List.of(lease));
        when(leases.findByUnitIdAndStatus(UNIT_ID, LeaseStatus.ACTIVE)).thenReturn(Optional.of(lease));
        when(tenants.findAll()).thenReturn(List.of(
                Tenant.builder().id(TENANT_ID).landlordId(LANDLORD).userId(TENANT_USER)
                        .fullName("Achieng Otieno").phone("254712345678").build()));
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).landlordId(LANDLORD).userId(TENANT_USER)
                        .fullName("Achieng Otieno").phone("254712345678").build()));
        when(requests.save(any())).thenAnswer(i -> {
            MaintenanceRequest r = i.getArgument(0, MaintenanceRequest.class);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        service = new MaintenanceService(requests, units, tenants, leases, publisher);
    }

    private RaiseMaintenanceRequest request() {
        return RaiseMaintenanceRequest.builder()
                .category("PLUMBING").title("Kitchen tap is leaking")
                .description("Water pools under the sink overnight.")
                .imageUrls(List.of("http://localhost:8080/api/documents/files/documents/maintenance_photo/a.jpg"))
                .build();
    }

    private MaintenanceRequest existing(MaintenanceStatus status) {
        MaintenanceRequest r = MaintenanceRequest.builder()
                .id(UUID.randomUUID()).unitId(UNIT_ID).leaseId(lease.getId()).tenantId(TENANT_ID)
                .landlordId(LANDLORD).reference("MNT-B7-ABC123").category("PLUMBING")
                .priority(MaintenancePriority.MEDIUM).title("Kitchen tap is leaking")
                .description("Water pools under the sink.").status(status).build();
        when(requests.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void aTenantRaisesAgainstTheUnitTheyActuallyRent() {
        var res = service.raiseAsTenant(TENANT_USER, request());

        assertEquals(UNIT_ID, res.getUnitId());
        assertEquals("B7", res.getUnitLabel());
        assertEquals("OPEN", res.getStatus());
        assertEquals("TENANT", res.getRaisedByRole());
        assertTrue(res.getReference().startsWith("MNT-B7-"));
        verify(publisher).publishMaintenanceRaised(any(), eq("B7"), eq(TENANT_USER));
    }

    @Test
    void aTenantResponseDoesNotEchoTheirOwnNameBack() {
        assertNull(service.raiseAsTenant(TENANT_USER, request()).getTenantName());
    }

    @Test
    void anAccountWithNoLinkedTenancyCannotRaiseAnything() {
        assertThrows(ForbiddenException.class, () -> service.raiseAsTenant(UUID.randomUUID(), request()));
    }

    @Test
    void aLinkedTenantWithNoActiveLeaseCannotRaiseAnything() {
        when(leases.findByStatus(LeaseStatus.ACTIVE)).thenReturn(List.of());
        assertThrows(ConflictException.class, () -> service.raiseAsTenant(TENANT_USER, request()));
    }

    @Test
    void aLandlordMustSayWhichUnit() {
        var req = request();
        req.setUnitId(null);
        assertThrows(ConflictException.class, () -> service.raiseAsLandlord(LANDLORD, req));
    }

    @Test
    void aLandlordCannotLogAJobOnSomeoneElsesUnit() {
        var req = request();
        req.setUnitId(UNIT_ID);
        assertThrows(ForbiddenException.class, () -> service.raiseAsLandlord(OTHER, req));
    }

    @Test
    void photosAreKeptWithTheRequest() {
        assertEquals(1, service.raiseAsTenant(TENANT_USER, request()).getImageUrls().size());
    }

    @Test
    void acknowledgingStampsTheTime() {
        MaintenanceRequest r = existing(MaintenanceStatus.OPEN);
        var res = service.update(LANDLORD, r.getId(), UpdateMaintenanceRequest.builder().status("ACKNOWLEDGED").build());
        assertEquals("ACKNOWLEDGED", res.getStatus());
        assertNotNull(res.getAcknowledgedAt());
    }

    @Test
    void resolvingTellsTheTenant() {
        MaintenanceRequest r = existing(MaintenanceStatus.IN_PROGRESS);
        var res = service.update(LANDLORD, r.getId(), UpdateMaintenanceRequest.builder()
                .status("RESOLVED").resolutionNotes("Washer replaced").cost(new BigDecimal("1200")).build());

        assertEquals("RESOLVED", res.getStatus());
        assertNotNull(res.getResolvedAt());
        assertEquals(0, new BigDecimal("1200").compareTo(res.getCost()));
        verify(publisher).publishMaintenanceResolved(any(), eq("B7"), eq(TENANT_USER));
    }

    @Test
    void aClosedRequestCannotBeReopened() {
        MaintenanceRequest r = existing(MaintenanceStatus.CLOSED);
        ConflictException e = assertThrows(ConflictException.class, () -> service.update(LANDLORD, r.getId(),
                UpdateMaintenanceRequest.builder().status("IN_PROGRESS").build()));
        assertTrue(e.getMessage().contains("closed"));
    }

    @Test
    void anOpenRequestCannotJumpStraightToResolved() {
        MaintenanceRequest r = existing(MaintenanceStatus.OPEN);
        assertThrows(ConflictException.class, () -> service.update(LANDLORD, r.getId(),
                UpdateMaintenanceRequest.builder().status("RESOLVED").build()),
                "a job has to be acknowledged or started before it can be called done");
    }

    @Test
    void aResolvedRequestCanBeReopenedIfTheFixDidNotHold() {
        MaintenanceRequest r = existing(MaintenanceStatus.RESOLVED);
        r.setResolvedAt(java.time.LocalDateTime.now());

        var res = service.update(LANDLORD, r.getId(), UpdateMaintenanceRequest.builder().status("IN_PROGRESS").build());

        assertEquals("IN_PROGRESS", res.getStatus());
        assertNull(res.getResolvedAt());
    }

    @Test
    void rejectingAlsoTellsTheTenant() {
        MaintenanceRequest r = existing(MaintenanceStatus.OPEN);
        var res = service.update(LANDLORD, r.getId(), UpdateMaintenanceRequest.builder()
                .status("REJECTED").resolutionNotes("Tenant damage, not a landlord repair").build());

        assertEquals("REJECTED", res.getStatus());
        verify(publisher).publishMaintenanceResolved(any(), any(), eq(TENANT_USER));
    }

    @Test
    void anotherLandlordCannotTouchTheRequest() {
        MaintenanceRequest r = existing(MaintenanceStatus.OPEN);
        assertThrows(ForbiddenException.class, () -> service.update(OTHER, r.getId(),
                UpdateMaintenanceRequest.builder().status("ACKNOWLEDGED").build()));
    }

    @Test
    void aLandlordLoggedJobDoesNotNotifyTheLandlordAboutTheirOwnRequest() {
        var req = request();
        req.setUnitId(UNIT_ID);
        service.raiseAsLandlord(LANDLORD, req);
        verify(publisher).publishMaintenanceRaised(any(), any(), any());
    }
}
