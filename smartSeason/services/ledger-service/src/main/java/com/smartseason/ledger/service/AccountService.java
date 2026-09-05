package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.Account;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.AccountRepository;
import com.smartseason.ledger.web.dto.AccountCreateRequest;
import com.smartseason.ledger.web.dto.AccountResponse;
import com.smartseason.ledger.web.dto.AccountUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private static final String RESOURCE = "Account";

    private final AccountRepository repository;
    private final EventPublisher events;

    public AccountService(AccountRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<AccountResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(AccountResponse::from));
    }

    public AccountResponse get(UUID id) {
        return AccountResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        Account entity = new Account();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setAccountCode(request.accountCode());
        entity.setName(request.name());
        entity.setAccountType(request.accountType());
        entity.setOwnerOrgId(request.ownerOrgId());
        entity.setOwnerUserId(request.ownerUserId());
        entity.setCurrency(request.currency());
        entity.setNormalBalance(request.normalBalance());
        entity.setStatus(request.status());
        entity.setParentAccountId(request.parentAccountId());

        Account saved = repository.save(entity);
        events.publish("money", "AccountCreated", saved.getId(), AccountResponse.from(saved));
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse update(UUID id, AccountUpdateRequest request) {
        Account entity = require(id);
        if (request.accountCode() != null) {
            entity.setAccountCode(request.accountCode());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.accountType() != null) {
            entity.setAccountType(request.accountType());
        }
        if (request.ownerOrgId() != null) {
            entity.setOwnerOrgId(request.ownerOrgId());
        }
        if (request.ownerUserId() != null) {
            entity.setOwnerUserId(request.ownerUserId());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.normalBalance() != null) {
            entity.setNormalBalance(request.normalBalance());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.parentAccountId() != null) {
            entity.setParentAccountId(request.parentAccountId());
        }

        Account saved = repository.save(entity);
        events.publish("money", "AccountUpdated", saved.getId(), AccountResponse.from(saved));
        return AccountResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Account entity = require(id);
        repository.delete(entity);
        events.publish("money", "AccountDeleted", id, null);
    }

    private Account require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
