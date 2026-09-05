package com.smartseason.search.repo;

import com.smartseason.search.domain.IndexJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexJobRepository extends JpaRepository<IndexJob, UUID> {

    Optional<IndexJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<IndexJob> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<IndexJob> findAllByIndexNameAndTenantId(String indexName, UUID tenantId, Pageable pageable);
}
