package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.FraudEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FraudEvidenceRepository extends JpaRepository<FraudEvidence, UUID> {

    Optional<FraudEvidence> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<FraudEvidence> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<FraudEvidence> findAllByCaseIdAndTenantId(UUID caseId, UUID tenantId, Pageable pageable);
}
