package com.smartseason.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.season.domain.SeasonStage;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.ReferenceChecker;
import com.smartseason.season.platform.DomainRuleException;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.platform.TenantMissingException;
import com.smartseason.season.repo.SeasonStageRepository;
import com.smartseason.season.web.dto.SeasonStageCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeasonStageServiceTest {

    private final SeasonStageRepository repository = mock(SeasonStageRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final SeasonStageService service = new SeasonStageService(repository, events, counts, ReferenceChecker.disabled());

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
        when(repository.save(any(SeasonStage.class))).thenAnswer(invocation -> {
            SeasonStage saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new SeasonStageCreateRequest(UUID.randomUUID(), "test", 1, null, null, null, null, SeasonStage.Status.PENDING, null));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("SeasonStageCreated"), any(), any());
    }

    @Test
    @DisplayName("create refuses an id that does not belong to the caller's tenant")
    void createRefusesForeignReference() {
        ReferenceChecker strict = mock(ReferenceChecker.class);
        org.mockito.Mockito.doThrow(new DomainRuleException("seasonId does not refer to a Season in your organisation"))
                .when(strict).require(eq("Season"), eq("seasonId"), any());
        SeasonStageService guarded = new SeasonStageService(repository, events, counts, strict);

        assertThatThrownBy(() -> guarded.create(new SeasonStageCreateRequest(UUID.randomUUID(), "test", 1, null, null, null, null, SeasonStage.Status.PENDING, null)))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("seasonId");

        verify(repository, org.mockito.Mockito.never()).save(any(SeasonStage.class));
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
