package com.smartseason.automation.repo;

import com.smartseason.automation.domain.AutomationRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

    Optional<AutomationRule> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<AutomationRule> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<AutomationRule> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
}
