package com.smartseason.agronomy.repo;

import com.smartseason.agronomy.domain.CropPlaybook;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CropPlaybookRepository extends JpaRepository<CropPlaybook, UUID> {

    Optional<CropPlaybook> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<CropPlaybook> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<CropPlaybook> findAllByCropCodeAndTenantId(String cropCode, UUID tenantId, Pageable pageable);
}
