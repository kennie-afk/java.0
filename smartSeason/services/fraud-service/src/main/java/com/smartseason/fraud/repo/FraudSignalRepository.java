package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.FraudSignal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FraudSignalRepository extends JpaRepository<FraudSignal, UUID> {

    Optional<FraudSignal> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<FraudSignal> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<FraudSignal> findAllBySubjectIdAndTenantId(UUID subjectId, UUID tenantId, Pageable pageable);
    Page<FraudSignal> findAllByRuleCodeAndTenantId(String ruleCode, UUID tenantId, Pageable pageable);
    Page<FraudSignal> findAllByCaseIdAndTenantId(UUID caseId, UUID tenantId, Pageable pageable);
}
