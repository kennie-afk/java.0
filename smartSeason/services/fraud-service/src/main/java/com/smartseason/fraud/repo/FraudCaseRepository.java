package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.FraudCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {

    Optional<FraudCase> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<FraudCase> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<FraudCase> findByCaseNumberAndTenantId(String caseNumber, UUID tenantId);
    Page<FraudCase> findAllBySubjectIdAndTenantId(UUID subjectId, UUID tenantId, Pageable pageable);
    Page<FraudCase> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
