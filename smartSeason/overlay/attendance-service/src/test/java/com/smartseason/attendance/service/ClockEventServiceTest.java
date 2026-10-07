package com.smartseason.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.attendance.clockin.ClockInPolicy;
import com.smartseason.attendance.clockin.GeofenceEvaluator;
import com.smartseason.attendance.domain.ClockEvent;
import com.smartseason.attendance.domain.Geofence;
import com.smartseason.attendance.repo.FarmGeofenceRepository;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.EventPublisher;
import com.smartseason.attendance.platform.ReferenceChecker;
import com.smartseason.attendance.platform.DomainRuleException;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.platform.TenantMissingException;
import com.smartseason.attendance.repo.ClockEventRepository;
import com.smartseason.attendance.web.dto.ClockEventCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockEventServiceTest {

    private final ClockEventRepository repository = mock(ClockEventRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final FarmGeofenceRepository geofences = mock(FarmGeofenceRepository.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final ClockEventService service = new ClockEventService(repository, events, counts, ReferenceChecker.disabled(), geofences, new GeofenceEvaluator(ClockInPolicy.defaults()));

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void bindTenant() {
        TenantContext.set(tenant);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create persists the entity against the caller's tenant and emits an event")
    void createStampsTenantAndPublishes() {
        when(repository.save(any(ClockEvent.class))).thenAnswer(invocation -> {
            ClockEvent saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new ClockEventCreateRequest(UUID.randomUUID(), UUID.randomUUID(), null, ClockEvent.EventType.CLOCK_IN, Instant.now(), Instant.now(), null, null, null, null, true, null, null, true, true, null, ClockEvent.Verdict.ACCEPTED, null));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("ClockEventCreated"), any(), any());
    }

    @Test
    @DisplayName("create refuses an id that does not belong to the caller's tenant")
    void createRefusesForeignReference() {
        ReferenceChecker strict = mock(ReferenceChecker.class);
        org.mockito.Mockito.doThrow(new DomainRuleException("shiftId does not refer to a Shift in your organisation"))
                .when(strict).require(eq("Shift"), eq("shiftId"), any());
        ClockEventService guarded = new ClockEventService(repository, events, counts, strict, geofences, new GeofenceEvaluator(ClockInPolicy.defaults()));

        assertThatThrownBy(() -> guarded.create(new ClockEventCreateRequest(UUID.randomUUID(), UUID.randomUUID(), null, ClockEvent.EventType.CLOCK_IN, Instant.now(), Instant.now(), null, null, null, null, true, null, null, true, true, null, ClockEvent.Verdict.ACCEPTED, null)))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("shiftId");

        verify(repository, org.mockito.Mockito.never()).save(any(ClockEvent.class));
    }

    @Test
    @DisplayName("a row belonging to another tenant reads as not found")
    void otherTenantRowIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("an unbound tenant fails closed instead of querying across tenants")
    void missingTenantFailsClosed() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(TenantMissingException.class);
    }

    // ---- the server, not the client, decides where a worker was ----

    private static final BigDecimal FARM_LAT = new BigDecimal("-1.2921");
    private static final BigDecimal FARM_LNG = new BigDecimal("36.8219");

    private Geofence fenceAround(UUID farmId, int radiusM) {
        Geofence fence = new Geofence();
        fence.setId(UUID.randomUUID());
        fence.setFarmId(farmId);
        fence.setActive(true);
        fence.setCenterLat(FARM_LAT);
        fence.setCenterLng(FARM_LNG);
        fence.setRadiusM(radiusM);
        return fence;
    }

    private ClockEvent createAt(UUID farm, BigDecimal lat, BigDecimal lng, BigDecimal accuracy, boolean mocked,
                                boolean claimsInside, ClockEvent.Verdict claimedVerdict) {
        when(repository.save(any(ClockEvent.class))).thenAnswer(invocation -> {
            ClockEvent saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        service.create(new ClockEventCreateRequest(UUID.randomUUID(), farm, null, ClockEvent.EventType.CLOCK_IN,
                Instant.now(), Instant.now(), lat, lng, accuracy, null, claimsInside, null, "device-1", mocked,
                false, null, claimedVerdict, "client says fine"));
        var captor = org.mockito.ArgumentCaptor.forClass(ClockEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a worker standing on the farm is accepted and matched to the fence, whatever the client claimed")
    void insideTheFenceIsAcceptedEvenIfTheClientSaidOtherwise() {
        UUID farm = UUID.randomUUID();
        Geofence fence = fenceAround(farm, 300);
        when(geofences.findAllByTenantIdAndFarmIdAndActive(tenant, farm, Boolean.TRUE)).thenReturn(java.util.List.of(fence));

        ClockEvent stored = createAt(farm, FARM_LAT, FARM_LNG, new BigDecimal("12"), false, false, ClockEvent.Verdict.REJECTED);

        assertThat(stored.getVerdict()).isEqualTo(ClockEvent.Verdict.ACCEPTED);
        assertThat(stored.getInsideGeofence()).isTrue();
        assertThat(stored.getGeofenceId()).isEqualTo(fence.getId());
        assertThat(stored.getFlagReason()).isNull();
    }

    @Test
    @DisplayName("a client claiming to be inside and accepted, from another town, is recorded as rejected")
    void aClientCannotClockInFromElsewhereByClaimingToBeInside() {
        UUID farm = UUID.randomUUID();
        when(geofences.findAllByTenantIdAndFarmIdAndActive(tenant, farm, Boolean.TRUE))
                .thenReturn(java.util.List.of(fenceAround(farm, 300)));

        ClockEvent stored = createAt(farm, new BigDecimal("-4.0435"), new BigDecimal("39.6682"), new BigDecimal("10"),
                false, true, ClockEvent.Verdict.ACCEPTED);

        assertThat(stored.getVerdict()).isEqualTo(ClockEvent.Verdict.REJECTED);
        assertThat(stored.getInsideGeofence()).isFalse();
        assertThat(stored.getGeofenceId()).isNull();
        assertThat(stored.getFlagReason()).contains("outside every active geofence");
        verify(events).publish(any(), eq("ClockEventCreated"), any(), any());
    }

    @Test
    @DisplayName("a device reporting a mocked location is rejected even from the middle of the farm")
    void aMockedLocationIsRejected() {
        UUID farm = UUID.randomUUID();
        when(geofences.findAllByTenantIdAndFarmIdAndActive(tenant, farm, Boolean.TRUE))
                .thenReturn(java.util.List.of(fenceAround(farm, 300)));

        ClockEvent stored = createAt(farm, FARM_LAT, FARM_LNG, new BigDecimal("5"), true, true, ClockEvent.Verdict.ACCEPTED);

        assertThat(stored.getVerdict()).isEqualTo(ClockEvent.Verdict.REJECTED);
        assertThat(stored.getFlagReason()).contains("mocked");
    }

    @Test
    @DisplayName("a fix too coarse to trust is flagged for a supervisor, not accepted")
    void aVagueFixIsFlagged() {
        UUID farm = UUID.randomUUID();
        when(geofences.findAllByTenantIdAndFarmIdAndActive(tenant, farm, Boolean.TRUE))
                .thenReturn(java.util.List.of(fenceAround(farm, 300)));

        ClockEvent stored = createAt(farm, FARM_LAT, FARM_LNG, new BigDecimal("250"), false, true, ClockEvent.Verdict.ACCEPTED);

        assertThat(stored.getVerdict()).isEqualTo(ClockEvent.Verdict.FLAGGED);
        assertThat(stored.getFlagReason()).contains("accuracy");
    }

    @Test
    @DisplayName("a farm with no fences drawn refuses every clock-in until one exists")
    void noFencesMeansNobodyIsInside() {
        UUID farm = UUID.randomUUID();
        when(geofences.findAllByTenantIdAndFarmIdAndActive(tenant, farm, Boolean.TRUE)).thenReturn(java.util.List.of());

        ClockEvent stored = createAt(farm, FARM_LAT, FARM_LNG, new BigDecimal("5"), false, true, ClockEvent.Verdict.ACCEPTED);

        assertThat(stored.getVerdict()).isEqualTo(ClockEvent.Verdict.REJECTED);
    }

    @Test
    @DisplayName("an update cannot turn a rejected clock-in into an accepted one")
    void theServersFindingCannotBeEditedAfterTheFact() {
        UUID id = UUID.randomUUID();
        ClockEvent rejected = new ClockEvent();
        rejected.setId(id);
        rejected.setTenantId(tenant);
        rejected.setVerdict(ClockEvent.Verdict.REJECTED);
        rejected.setInsideGeofence(false);
        rejected.setFlagReason("position falls outside every active geofence for the farm");
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(rejected));
        when(repository.save(any(ClockEvent.class))).thenAnswer(invocation -> {
            ClockEvent saved = invocation.getArgument(0);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        service.update(id, new com.smartseason.attendance.web.dto.ClockEventUpdateRequest(null, null, null, null, null,
                null, null, null, null, UUID.randomUUID(), true, null, null, null, null, null,
                ClockEvent.Verdict.ACCEPTED, "approved by the worker"));

        assertThat(rejected.getVerdict()).isEqualTo(ClockEvent.Verdict.REJECTED);
        assertThat(rejected.getInsideGeofence()).isFalse();
        assertThat(rejected.getFlagReason()).contains("outside every active geofence");
    }
}
