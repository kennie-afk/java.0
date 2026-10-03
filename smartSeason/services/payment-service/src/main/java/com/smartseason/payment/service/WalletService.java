package com.smartseason.payment.service;

import com.smartseason.payment.domain.Wallet;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.ReferenceChecker;
import com.smartseason.payment.platform.Cursor;
import com.smartseason.payment.platform.CursorPage;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.WalletRepository;
import com.smartseason.payment.web.dto.WalletCreateRequest;
import com.smartseason.payment.web.dto.WalletResponse;
import com.smartseason.payment.web.dto.WalletUpdateRequest;
import com.smartseason.payment.platform.ListFilter;
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
public class WalletService {

    private static final String RESOURCE = "Wallet";
    private static final String ENTITY = "wallets";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("ownerOrgId", UUID.class),
            Map.entry("ownerUserId", UUID.class),
            Map.entry("currency", String.class),
            Map.entry("status", Wallet.Status.class));

    private static final List<String> SEARCHABLE = List.of("currency");

    private final WalletRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public WalletService(WalletRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<WalletResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Wallet>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(WalletResponse::from));
    }

    public PageResponse<WalletResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WalletResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<WalletResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Wallet> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(WalletResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "WalletDeleted", id, null);
    }

    private Wallet require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
