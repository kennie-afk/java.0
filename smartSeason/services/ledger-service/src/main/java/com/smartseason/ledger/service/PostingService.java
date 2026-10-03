package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.Posting;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.CountCache;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.Cursor;
import com.smartseason.ledger.platform.CursorPage;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.PostingRepository;
import com.smartseason.ledger.web.dto.PostingCreateRequest;
import com.smartseason.ledger.web.dto.PostingResponse;
import com.smartseason.ledger.web.dto.PostingUpdateRequest;
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
public class PostingService {

    private static final String RESOURCE = "Posting";
    private static final String ENTITY = "postings";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("journalEntryId", UUID.class),
            Map.entry("accountId", UUID.class),
            Map.entry("accountCode", String.class),
            Map.entry("direction", Posting.Direction.class),
            Map.entry("currency", String.class),
            Map.entry("memo", String.class));

    private static final List<String> SEARCHABLE = List.of("accountCode", "currency", "memo");

    private final PostingRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public PostingService(PostingRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<PostingResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Posting>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(PostingResponse::from));
    }

    public PageResponse<PostingResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(PostingResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<PostingResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Posting> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(PostingResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public PostingResponse get(UUID id) {
        return PostingResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public PostingResponse create(PostingCreateRequest request) {
        Posting entity = new Posting();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setJournalEntryId(request.journalEntryId());
        entity.setAccountId(request.accountId());
        entity.setAccountCode(request.accountCode());
        entity.setDirection(request.direction());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setPostedAt(request.postedAt());
        entity.setMemo(request.memo());

        Posting saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "PostingCreated", saved.getId(), PostingResponse.from(saved));
        return PostingResponse.from(saved);
    }

    @Transactional
    public PostingResponse update(UUID id, PostingUpdateRequest request) {
        Posting entity = require(id);
        if (request.journalEntryId() != null) {
            entity.setJournalEntryId(request.journalEntryId());
        }
        if (request.accountId() != null) {
            entity.setAccountId(request.accountId());
        }
        if (request.accountCode() != null) {
            entity.setAccountCode(request.accountCode());
        }
        if (request.direction() != null) {
            entity.setDirection(request.direction());
        }
        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.postedAt() != null) {
            entity.setPostedAt(request.postedAt());
        }
        if (request.memo() != null) {
            entity.setMemo(request.memo());
        }

        Posting saved = repository.save(entity);
        events.publish("money", "PostingUpdated", saved.getId(), PostingResponse.from(saved));
        return PostingResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Posting entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "PostingDeleted", id, null);
    }

    private Posting require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
