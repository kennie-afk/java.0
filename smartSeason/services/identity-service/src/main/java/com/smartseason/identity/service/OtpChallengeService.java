package com.smartseason.identity.service;

import com.smartseason.identity.domain.OtpChallenge;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.OtpChallengeRepository;
import com.smartseason.identity.web.dto.OtpChallengeCreateRequest;
import com.smartseason.identity.web.dto.OtpChallengeResponse;
import com.smartseason.identity.web.dto.OtpChallengeUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OtpChallengeService {

    private static final String RESOURCE = "OtpChallenge";
    private static final String ENTITY = "otp_challenges";

    private final OtpChallengeRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public OtpChallengeService(OtpChallengeRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<OtpChallengeResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(OtpChallengeResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public OtpChallengeResponse get(UUID id) {
        return OtpChallengeResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public OtpChallengeResponse create(OtpChallengeCreateRequest request) {
        OtpChallenge entity = new OtpChallenge();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setUserId(request.userId());
        entity.setDestination(request.destination());
        entity.setChannel(request.channel());
        entity.setCodeHash(request.codeHash());
        entity.setPurpose(request.purpose());
        entity.setExpiresAt(request.expiresAt());
        entity.setConsumedAt(request.consumedAt());
        entity.setAttempts(request.attempts());

        OtpChallenge saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("identity", "OtpChallengeCreated", saved.getId(), OtpChallengeResponse.from(saved));
        return OtpChallengeResponse.from(saved);
    }

    @Transactional
    public OtpChallengeResponse update(UUID id, OtpChallengeUpdateRequest request) {
        OtpChallenge entity = require(id);
        if (request.userId() != null) {
            entity.setUserId(request.userId());
        }
        if (request.destination() != null) {
            entity.setDestination(request.destination());
        }
        if (request.channel() != null) {
            entity.setChannel(request.channel());
        }
        if (request.codeHash() != null) {
            entity.setCodeHash(request.codeHash());
        }
        if (request.purpose() != null) {
            entity.setPurpose(request.purpose());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.consumedAt() != null) {
            entity.setConsumedAt(request.consumedAt());
        }
        if (request.attempts() != null) {
            entity.setAttempts(request.attempts());
        }

        OtpChallenge saved = repository.save(entity);
        events.publish("identity", "OtpChallengeUpdated", saved.getId(), OtpChallengeResponse.from(saved));
        return OtpChallengeResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        OtpChallenge entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("identity", "OtpChallengeDeleted", id, null);
    }

    private OtpChallenge require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
