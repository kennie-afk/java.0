package com.smartseason.workforce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.workforce.domain.WorkerContract;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.ReferenceChecker;
import com.smartseason.workforce.platform.DomainRuleException;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.platform.TenantMissingException;
import com.smartseason.workforce.repo.WorkerContractRepository;
import com.smartseason.workforce.web.dto.WorkerContractCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkerContractServiceTest {

    private final WorkerContractRepository repository = mock(WorkerContractRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final WorkerContractService service = new WorkerContractService(repository, events, counts, ReferenceChecker.disabled());

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
        when(repository.save(any(WorkerContract.class))).thenAnswer(invocation -> {
            WorkerContract saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new WorkerContractCreateRequest(UUID.randomUUID(), UUID.randomUUID(), WorkerContract.ContractType.CASUAL, LocalDate.now(), null, null, null, null, null, WorkerContract.Status.DRAFT, null));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("WorkerContractCreated"), any(), any());
    }

    @Test
    @DisplayName("create refuses an id that does not belong to the caller's tenant")
    void createRefusesForeignReference() {
        ReferenceChecker strict = mock(ReferenceChecker.class);
        org.mockito.Mockito.doThrow(new DomainRuleException("workerId does not refer to a Worker in your organisation"))
                .when(strict).require(eq("Worker"), eq("workerId"), any());
        WorkerContractService guarded = new WorkerContractService(repository, events, counts, strict);

        assertThatThrownBy(() -> guarded.create(new WorkerContractCreateRequest(UUID.randomUUID(), UUID.randomUUID(), WorkerContract.ContractType.CASUAL, LocalDate.now(), null, null, null, null, null, WorkerContract.Status.DRAFT, null)))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("workerId");

        verify(repository, org.mockito.Mockito.never()).save(any(WorkerContract.class));
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
