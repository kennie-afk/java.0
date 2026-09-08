package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.JournalEntry;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.JournalEntryRepository;
import com.smartseason.ledger.web.dto.JournalEntryCreateRequest;
import com.smartseason.ledger.web.dto.JournalEntryResponse;
import com.smartseason.ledger.web.dto.JournalEntryUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JournalEntryService {

    private static final String RESOURCE = "JournalEntry";
    private static final String ENTITY = "journal_entries";

    private final JournalEntryRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public JournalEntryService(JournalEntryRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<JournalEntryResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(JournalEntryResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public JournalEntryResponse get(UUID id) {
        return JournalEntryResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public JournalEntryResponse create(JournalEntryCreateRequest request) {
        JournalEntry entity = new JournalEntry();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setEntryNumber(request.entryNumber());
        entity.setDescription(request.description());
        entity.setSourceEvent(request.sourceEvent());
        entity.setSourceRef(request.sourceRef());
        entity.setPostedAt(request.postedAt());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setCurrency(request.currency());
        entity.setTotalDebit(request.totalDebit());
        entity.setTotalCredit(request.totalCredit());
        entity.setBalanced(request.balanced());
        entity.setReversalOfId(request.reversalOfId());
        entity.setIdempotencyKey(request.idempotencyKey());

        JournalEntry saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "JournalEntryCreated", saved.getId(), JournalEntryResponse.from(saved));
        return JournalEntryResponse.from(saved);
    }

    @Transactional
    public JournalEntryResponse update(UUID id, JournalEntryUpdateRequest request) {
        JournalEntry entity = require(id);
        if (request.entryNumber() != null) {
            entity.setEntryNumber(request.entryNumber());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.sourceEvent() != null) {
            entity.setSourceEvent(request.sourceEvent());
        }
        if (request.sourceRef() != null) {
            entity.setSourceRef(request.sourceRef());
        }
        if (request.postedAt() != null) {
            entity.setPostedAt(request.postedAt());
        }
        if (request.effectiveDate() != null) {
            entity.setEffectiveDate(request.effectiveDate());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.totalDebit() != null) {
            entity.setTotalDebit(request.totalDebit());
        }
        if (request.totalCredit() != null) {
            entity.setTotalCredit(request.totalCredit());
        }
        if (request.balanced() != null) {
            entity.setBalanced(request.balanced());
        }
        if (request.reversalOfId() != null) {
            entity.setReversalOfId(request.reversalOfId());
        }
        if (request.idempotencyKey() != null) {
            entity.setIdempotencyKey(request.idempotencyKey());
        }

        JournalEntry saved = repository.save(entity);
        events.publish("money", "JournalEntryUpdated", saved.getId(), JournalEntryResponse.from(saved));
        return JournalEntryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        JournalEntry entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "JournalEntryDeleted", id, null);
    }

    private JournalEntry require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
