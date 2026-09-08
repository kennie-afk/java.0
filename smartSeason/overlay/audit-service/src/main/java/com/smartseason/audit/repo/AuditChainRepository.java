package com.smartseason.audit.repo;

import com.smartseason.audit.domain.AuditRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Chain-ordered reads. Kept apart from the generated repository so a
 * regeneration cannot drop them.
 */
@Repository
public interface AuditChainRepository extends JpaRepository<AuditRecord, UUID> {

    Optional<AuditRecord> findFirstByTenantIdOrderBySequenceDesc(UUID tenantId);

    List<AuditRecord> findAllByTenantIdOrderBySequenceAsc(UUID tenantId);

    List<AuditRecord> findAllByTenantIdAndResourceIdOrderBySequenceAsc(UUID tenantId, String resourceId);
}
