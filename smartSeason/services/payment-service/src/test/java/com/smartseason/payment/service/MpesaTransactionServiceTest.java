package com.smartseason.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.payment.domain.MpesaTransaction;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.platform.TenantMissingException;
import com.smartseason.payment.repo.MpesaTransactionRepository;
import com.smartseason.payment.web.dto.MpesaTransactionCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MpesaTransactionServiceTest {

    private final MpesaTransactionRepository repository = mock(MpesaTransactionRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final MpesaTransactionService service = new MpesaTransactionService(repository, events);

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
        when(repository.save(any(MpesaTransaction.class))).thenAnswer(invocation -> {
            MpesaTransaction saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new MpesaTransactionCreateRequest(null, null, null, null, null, BigDecimal.ONE, MpesaTransaction.TransactionType.STK_PUSH, null, null, null, null, null, MpesaTransaction.Status.INITIATED));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("MpesaTransactionCreated"), any(), any());
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
