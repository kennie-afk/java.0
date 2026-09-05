package com.smartseason.audit.repo;

import com.smartseason.audit.domain.AuditAnchor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditAnchorRepository extends JpaRepository<AuditAnchor, UUID> {

    Optional<AuditAnchor> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<AuditAnchor> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

}
