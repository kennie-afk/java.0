package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.FraudRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

    Optional<FraudRule> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<FraudRule> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<FraudRule> findByCodeAndTenantId(String code, UUID tenantId);
}
