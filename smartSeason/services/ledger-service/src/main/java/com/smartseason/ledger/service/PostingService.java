package com.smartseason.ledger.service;

import com.smartseason.ledger.domain.Posting;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.PostingRepository;
import com.smartseason.ledger.web.dto.PostingCreateRequest;
import com.smartseason.ledger.web.dto.PostingResponse;
import com.smartseason.ledger.web.dto.PostingUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostingService {

    private static final String RESOURCE = "Posting";

    private final PostingRepository repository;
    private final EventPublisher events;

    public PostingService(PostingRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<PostingResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(PostingResponse::from));
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
        events.publish("money", "PostingDeleted", id, null);
    }

    private Posting require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
