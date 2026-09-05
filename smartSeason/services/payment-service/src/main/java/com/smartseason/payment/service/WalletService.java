package com.smartseason.payment.service;

import com.smartseason.payment.domain.Wallet;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.WalletRepository;
import com.smartseason.payment.web.dto.WalletCreateRequest;
import com.smartseason.payment.web.dto.WalletResponse;
import com.smartseason.payment.web.dto.WalletUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WalletService {

    private static final String RESOURCE = "Wallet";

    private final WalletRepository repository;
    private final EventPublisher events;

    public WalletService(WalletRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<WalletResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(WalletResponse::from));
    }

    public WalletResponse get(UUID id) {
        return WalletResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WalletResponse create(WalletCreateRequest request) {
        Wallet entity = new Wallet();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setOwnerOrgId(request.ownerOrgId());
        entity.setOwnerUserId(request.ownerUserId());
        entity.setCurrency(request.currency());
        entity.setBalance(request.balance());
        entity.setAvailableBalance(request.availableBalance());
        entity.setStatus(request.status());
        entity.setLastTransactionAt(request.lastTransactionAt());

        Wallet saved = repository.save(entity);
        events.publish("money", "WalletCreated", saved.getId(), WalletResponse.from(saved));
        return WalletResponse.from(saved);
    }

    @Transactional
    public WalletResponse update(UUID id, WalletUpdateRequest request) {
        Wallet entity = require(id);
        if (request.ownerOrgId() != null) {
            entity.setOwnerOrgId(request.ownerOrgId());
        }
        if (request.ownerUserId() != null) {
            entity.setOwnerUserId(request.ownerUserId());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.balance() != null) {
            entity.setBalance(request.balance());
        }
        if (request.availableBalance() != null) {
            entity.setAvailableBalance(request.availableBalance());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.lastTransactionAt() != null) {
            entity.setLastTransactionAt(request.lastTransactionAt());
        }

        Wallet saved = repository.save(entity);
        events.publish("money", "WalletUpdated", saved.getId(), WalletResponse.from(saved));
        return WalletResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Wallet entity = require(id);
        repository.delete(entity);
        events.publish("money", "WalletDeleted", id, null);
    }

    private Wallet require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
