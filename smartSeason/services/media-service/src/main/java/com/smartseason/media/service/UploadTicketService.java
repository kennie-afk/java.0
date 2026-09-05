package com.smartseason.media.service;

import com.smartseason.media.domain.UploadTicket;
import com.smartseason.media.platform.EventPublisher;
import com.smartseason.media.platform.PageResponse;
import com.smartseason.media.platform.ResourceNotFoundException;
import com.smartseason.media.platform.TenantContext;
import com.smartseason.media.repo.UploadTicketRepository;
import com.smartseason.media.web.dto.UploadTicketCreateRequest;
import com.smartseason.media.web.dto.UploadTicketResponse;
import com.smartseason.media.web.dto.UploadTicketUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UploadTicketService {

    private static final String RESOURCE = "UploadTicket";

    private final UploadTicketRepository repository;
    private final EventPublisher events;

    public UploadTicketService(UploadTicketRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<UploadTicketResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(UploadTicketResponse::from));
    }

    public UploadTicketResponse get(UUID id) {
        return UploadTicketResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public UploadTicketResponse create(UploadTicketCreateRequest request) {
        UploadTicket entity = new UploadTicket();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setStorageKey(request.storageKey());
        entity.setUploadUrl(request.uploadUrl());
        entity.setMethod(request.method());
        entity.setRequestedBy(request.requestedBy());
        entity.setContentType(request.contentType());
        entity.setMaxSizeBytes(request.maxSizeBytes());
        entity.setExpiresAt(request.expiresAt());
        entity.setConsumedAt(request.consumedAt());
        entity.setStatus(request.status());

        UploadTicket saved = repository.save(entity);
        events.publish("platform", "UploadTicketCreated", saved.getId(), UploadTicketResponse.from(saved));
        return UploadTicketResponse.from(saved);
    }

    @Transactional
    public UploadTicketResponse update(UUID id, UploadTicketUpdateRequest request) {
        UploadTicket entity = require(id);
        if (request.storageKey() != null) {
            entity.setStorageKey(request.storageKey());
        }
        if (request.uploadUrl() != null) {
            entity.setUploadUrl(request.uploadUrl());
        }
        if (request.method() != null) {
            entity.setMethod(request.method());
        }
        if (request.requestedBy() != null) {
            entity.setRequestedBy(request.requestedBy());
        }
        if (request.contentType() != null) {
            entity.setContentType(request.contentType());
        }
        if (request.maxSizeBytes() != null) {
            entity.setMaxSizeBytes(request.maxSizeBytes());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.consumedAt() != null) {
            entity.setConsumedAt(request.consumedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        UploadTicket saved = repository.save(entity);
        events.publish("platform", "UploadTicketUpdated", saved.getId(), UploadTicketResponse.from(saved));
        return UploadTicketResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UploadTicket entity = require(id);
        repository.delete(entity);
        events.publish("platform", "UploadTicketDeleted", id, null);
    }

    private UploadTicket require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
