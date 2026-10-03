package com.smartseason.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.order.domain.Dispute;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.ReferenceChecker;
import com.smartseason.order.platform.DomainRuleException;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.platform.TenantMissingException;
import com.smartseason.order.repo.DisputeRepository;
import com.smartseason.order.web.dto.DisputeCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisputeServiceTest {

    private final DisputeRepository repository = mock(DisputeRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final DisputeService service = new DisputeService(repository, events, counts, ReferenceChecker.disabled());

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
        when(repository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new DisputeCreateRequest(UUID.randomUUID(), UUID.randomUUID(), Dispute.Category.QUALITY, "test", Instant.now(), Dispute.Status.OPEN, null, null, null));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("DisputeCreated"), any(), any());
    }

    @Test
    @DisplayName("create refuses an id that does not belong to the caller's tenant")
    void createRefusesForeignReference() {
        ReferenceChecker strict = mock(ReferenceChecker.class);
        org.mockito.Mockito.doThrow(new DomainRuleException("orderId does not refer to a PurchaseOrder in your organisation"))
                .when(strict).require(eq("PurchaseOrder"), eq("orderId"), any());
        DisputeService guarded = new DisputeService(repository, events, counts, strict);

        assertThatThrownBy(() -> guarded.create(new DisputeCreateRequest(UUID.randomUUID(), UUID.randomUUID(), Dispute.Category.QUALITY, "test", Instant.now(), Dispute.Status.OPEN, null, null, null)))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("orderId");

        verify(repository, org.mockito.Mockito.never()).save(any(Dispute.class));
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
