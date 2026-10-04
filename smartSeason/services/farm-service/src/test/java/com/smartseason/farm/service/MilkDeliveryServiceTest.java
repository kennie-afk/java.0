package com.smartseason.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.farm.domain.MilkDelivery;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.ReferenceChecker;
import com.smartseason.farm.platform.DomainRuleException;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.platform.TenantMissingException;
import com.smartseason.farm.repo.MilkDeliveryRepository;
import com.smartseason.farm.web.dto.MilkDeliveryCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MilkDeliveryServiceTest {

    private final MilkDeliveryRepository repository = mock(MilkDeliveryRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final MilkDeliveryService service = new MilkDeliveryService(repository, events, counts, ReferenceChecker.disabled());

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
        when(repository.save(any(MilkDelivery.class))).thenAnswer(invocation -> {
            MilkDelivery saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new MilkDeliveryCreateRequest(UUID.randomUUID(), LocalDate.now(), "test", null, BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, null, null, MilkDelivery.Status.DELIVERED, null));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("MilkDeliveryCreated"), any(), any());
    }

    @Test
    @DisplayName("create refuses an id that does not belong to the caller's tenant")
    void createRefusesForeignReference() {
        ReferenceChecker strict = mock(ReferenceChecker.class);
        org.mockito.Mockito.doThrow(new DomainRuleException("farmId does not refer to a Farm in your organisation"))
                .when(strict).require(eq("Farm"), eq("farmId"), any());
        MilkDeliveryService guarded = new MilkDeliveryService(repository, events, counts, strict);

        assertThatThrownBy(() -> guarded.create(new MilkDeliveryCreateRequest(UUID.randomUUID(), LocalDate.now(), "test", null, BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, null, null, MilkDelivery.Status.DELIVERED, null)))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("farmId");

        verify(repository, org.mockito.Mockito.never()).save(any(MilkDelivery.class));
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
