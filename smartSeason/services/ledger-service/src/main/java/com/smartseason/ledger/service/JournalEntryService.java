package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.JournalEntry;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.ReferenceChecker;
import com.smartseason.ledger.platform.Cursor;
import com.smartseason.ledger.platform.CursorPage;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.JournalEntryRepository;
import com.smartseason.ledger.web.dto.JournalEntryCreateRequest;
import com.smartseason.ledger.web.dto.JournalEntryResponse;
import com.smartseason.ledger.web.dto.JournalEntryUpdateRequest;
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
public class JournalEntryService {

    private static final String RESOURCE = "JournalEntry";
    private static final String ENTITY = "journal_entries";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("entryNumber", String.class),
            Map.entry("description", String.class),
            Map.entry("sourceEvent", String.class),
            Map.entry("sourceRef", String.class),
            Map.entry("currency", String.class),
            Map.entry("balanced", Boolean.class),
            Map.entry("reversalOfId", UUID.class),
            Map.entry("idempotencyKey", String.class));

    private static final List<String> SEARCHABLE = List.of("entryNumber", "description", "sourceEvent", "sourceRef", "currency", "idempotencyKey");

    private final JournalEntryRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public JournalEntryService(JournalEntryRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<JournalEntryResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<JournalEntry>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(JournalEntryResponse::from));
    }

    public PageResponse<JournalEntryResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(JournalEntryResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<JournalEntryResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<JournalEntry> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(JournalEntryResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("JournalEntry", "reversalOfId", request.reversalOfId());
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
        references.require("JournalEntry", "reversalOfId", request.reversalOfId());
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
