package com.smartseason.telemetryingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.telemetryingest.domain.TelemetryAnomalyRecord;
import com.smartseason.telemetryingest.platform.EventPublisher;
import com.smartseason.telemetryingest.platform.ResourceNotFoundException;
import com.smartseason.telemetryingest.platform.TenantContext;
import com.smartseason.telemetryingest.platform.TenantMissingException;
import com.smartseason.telemetryingest.repo.TelemetryAnomalyRecordRepository;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryAnomalyRecordServiceTest {

    private final TelemetryAnomalyRecordRepository repository = mock(TelemetryAnomalyRecordRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final TelemetryAnomalyRecordService service = new TelemetryAnomalyRecordService(repository, events);

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
        when(repository.save(any(TelemetryAnomalyRecord.class))).thenAnswer(invocation -> {
            TelemetryAnomalyRecord saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new TelemetryAnomalyRecordCreateRequest(UUID.randomUUID(), null, "test", BigDecimal.ONE, null, null, Instant.now(), TelemetryAnomalyRecord.Severity.LOW, true));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("TelemetryAnomalyRecordCreated"), any(), any());
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
