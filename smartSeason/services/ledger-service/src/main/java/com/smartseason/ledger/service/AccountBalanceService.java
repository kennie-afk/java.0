package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.AccountBalance;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.ReferenceChecker;
import com.smartseason.ledger.platform.Cursor;
import com.smartseason.ledger.platform.CursorPage;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.AccountBalanceRepository;
import com.smartseason.ledger.web.dto.AccountBalanceCreateRequest;
import com.smartseason.ledger.web.dto.AccountBalanceResponse;
import com.smartseason.ledger.web.dto.AccountBalanceUpdateRequest;
import com.smartseason.ledger.platform.ListFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountBalanceService {

    private static final String RESOURCE = "AccountBalance";
    private static final String ENTITY = "account_balances";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("accountId", UUID.class),
            Map.entry("accountCode", String.class),
            Map.entry("currency", String.class));

    private static final List<String> SEARCHABLE = List.of("accountCode", "currency");

    private final AccountBalanceRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public AccountBalanceService(AccountBalanceRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<AccountBalanceResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<AccountBalance>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(AccountBalanceResponse::from));
    }

    public PageResponse<AccountBalanceResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(AccountBalanceResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<AccountBalanceResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<AccountBalance> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(AccountBalanceResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("Account", "accountId", request.accountId());
        entity.setAccountId(request.accountId());
        entity.setAccountCode(request.accountCode());
        entity.setCurrency(request.currency());
        entity.setDebitTotal(request.debitTotal());
        entity.setCreditTotal(request.creditTotal());
        entity.setBalance(request.balance());
        entity.setPostingCount(request.postingCount());
        entity.setLastPostedAt(request.lastPostedAt());

        AccountBalance saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "AccountBalanceCreated", saved.getId(), AccountBalanceResponse.from(saved));
        return AccountBalanceResponse.from(saved);
    }

    @Transactional
    public AccountBalanceResponse update(UUID id, AccountBalanceUpdateRequest request) {
        AccountBalance entity = require(id);
        references.require("Account", "accountId", request.accountId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "AccountBalanceDeleted", id, null);
    }

    private AccountBalance require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
