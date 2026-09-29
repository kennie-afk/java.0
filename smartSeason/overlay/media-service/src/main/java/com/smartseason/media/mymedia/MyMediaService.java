package com.smartseason.media.mymedia;

import com.smartseason.media.domain.MediaAsset;
import com.smartseason.media.platform.ResourceNotFoundException;
import com.smartseason.media.platform.TenantContext;
import com.smartseason.media.repo.MyMediaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The media assets one account uploaded. Read-only: this is about closing the
 * read-side gap (a worker listing every uploader's media via the generic
 * catalogue screen), not a new upload flow. Same not-found-not-forbidden
 * shape as task-service's MyWorkService.
 */
@Service
@Transactional(readOnly = true)
public class MyMediaService {

    private final MyMediaRepository repository;

    public MyMediaService(MyMediaRepository repository) {
        this.repository = repository;
    }

    public List<MediaAsset> mine(UUID callerUserId) {
        return repository.findAllByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(
                TenantContext.requireTenantId(), callerUserId);
    }

    public MediaAsset one(UUID id, UUID callerUserId) {
        return repository.findByIdAndTenantIdAndOwnerUserId(id, TenantContext.requireTenantId(), callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("MediaAsset", id));
    }
}
