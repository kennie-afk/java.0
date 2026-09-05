package com.smartseason.traceability.repo;

import com.smartseason.traceability.domain.CertEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertEvidenceRepository extends JpaRepository<CertEvidence, UUID> {

    Optional<CertEvidence> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<CertEvidence> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<CertEvidence> findAllByBatchCodeAndTenantId(String batchCode, UUID tenantId, Pageable pageable);
    Page<CertEvidence> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
