package com.smartseason.traceability.repo;

import com.smartseason.traceability.domain.TraceLink;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TraceLinkRepository extends JpaRepository<TraceLink, UUID> {

    Optional<TraceLink> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<TraceLink> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<TraceLink> findAllByBatchCodeAndTenantId(String batchCode, UUID tenantId, Pageable pageable);
}
