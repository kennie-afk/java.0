package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.AccountBalance;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.AccountBalanceRepository;
import com.smartseason.ledger.web.dto.AccountBalanceCreateRequest;
import com.smartseason.ledger.web.dto.AccountBalanceResponse;
import com.smartseason.ledger.web.dto.AccountBalanceUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountBalanceService {

    private static final String RESOURCE = "AccountBalance";

    private final AccountBalanceRepository repository;
    private final EventPublisher events;

    public AccountBalanceService(AccountBalanceRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<AccountBalanceResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(AccountBalanceResponse::from));
    }

    public AccountBalanceResponse get(UUID id) {
        return AccountBalanceResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public AccountBalanceResponse create(AccountBalanceCreateRequest request) {
        AccountBalance entity = new AccountBalance();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setAccountId(request.accountId());
        entity.setAccountCode(request.accountCode());
        entity.setCurrency(request.currency());
        entity.setDebitTotal(request.debitTotal());
        entity.setCreditTotal(request.creditTotal());
        entity.setBalance(request.balance());
        entity.setPostingCount(request.postingCount());
        entity.setLastPostedAt(request.lastPostedAt());

        AccountBalance saved = repository.save(entity);
        events.publish("money", "AccountBalanceCreated", saved.getId(), AccountBalanceResponse.from(saved));
        return AccountBalanceResponse.from(saved);
    }

    @Transactional
    public AccountBalanceResponse update(UUID id, AccountBalanceUpdateRequest request) {
        AccountBalance entity = require(id);
        if (request.accountId() != null) {
            entity.setAccountId(request.accountId());
        }
        if (request.accountCode() != null) {
            entity.setAccountCode(request.accountCode());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.debitTotal() != null) {
            entity.setDebitTotal(request.debitTotal());
        }
        if (request.creditTotal() != null) {
            entity.setCreditTotal(request.creditTotal());
        }
        if (request.balance() != null) {
            entity.setBalance(request.balance());
        }
        if (request.postingCount() != null) {
            entity.setPostingCount(request.postingCount());
        }
        if (request.lastPostedAt() != null) {
            entity.setLastPostedAt(request.lastPostedAt());
        }

        AccountBalance saved = repository.save(entity);
        events.publish("money", "AccountBalanceUpdated", saved.getId(), AccountBalanceResponse.from(saved));
        return AccountBalanceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        AccountBalance entity = require(id);
        repository.delete(entity);
        events.publish("money", "AccountBalanceDeleted", id, null);
    }

    private AccountBalance require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
