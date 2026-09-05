package com.smartseason.task.repo;

import com.smartseason.task.domain.ChecklistItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    Optional<ChecklistItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ChecklistItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ChecklistItem> findAllByWorkOrderIdAndTenantId(UUID workOrderId, UUID tenantId, Pageable pageable);
}
