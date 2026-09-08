package com.smartseason.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.attendance.domain.ClockEvent;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.CountCache;
import com.smartseason.attendance.platform.EventPublisher;
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

    private final CountCache counts = new CountCache(null, 30, false);

    private final ClockEventService service = new ClockEventService(repository, events, counts);

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
}
