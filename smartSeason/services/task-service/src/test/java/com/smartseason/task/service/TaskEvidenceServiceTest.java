package com.smartseason.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.task.domain.TaskEvidence;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.CountCache;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.ReferenceChecker;
import com.smartseason.task.platform.DomainRuleException;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.platform.TenantMissingException;
import com.smartseason.task.repo.TaskEvidenceRepository;
import com.smartseason.task.web.dto.TaskEvidenceCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskEvidenceServiceTest {

    private final TaskEvidenceRepository repository = mock(TaskEvidenceRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final TaskEvidenceService service = new TaskEvidenceService(repository, events, counts, ReferenceChecker.disabled());

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
        when(repository.save(any(TaskEvidence.class))).thenAnswer(invocation -> {
            TaskEvidence saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new TaskEvidenceCreateRequest(UUID.randomUUID(), null, TaskEvidence.EvidenceType.PHOTO, null, null, null, null, null, null, true, null, TaskEvidence.Verdict.PENDING));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("TaskEvidenceCreated"), any(), any());
    }

    @Test
    @DisplayName("create refuses an id that does not belong to the caller's tenant")
    void createRefusesForeignReference() {
        ReferenceChecker strict = mock(ReferenceChecker.class);
        org.mockito.Mockito.doThrow(new DomainRuleException("assignmentId does not refer to a TaskAssignment in your organisation"))
                .when(strict).require(eq("TaskAssignment"), eq("assignmentId"), any());
        TaskEvidenceService guarded = new TaskEvidenceService(repository, events, counts, strict);

        assertThatThrownBy(() -> guarded.create(new TaskEvidenceCreateRequest(UUID.randomUUID(), null, TaskEvidence.EvidenceType.PHOTO, null, null, null, null, null, null, true, null, TaskEvidence.Verdict.PENDING)))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("assignmentId");

        verify(repository, org.mockito.Mockito.never()).save(any(TaskEvidence.class));
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
