package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.InputIssue;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InputIssueRepository extends JpaRepository<InputIssue, UUID> {

    Optional<InputIssue> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<InputIssue> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<InputIssue> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<InputIssue> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<InputIssue> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
    Page<InputIssue> findAllByInputCodeAndTenantId(String inputCode, UUID tenantId, Pageable pageable);
}
