package com.smartseason.season.repo;

import com.smartseason.season.domain.StageTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StageTemplateRepository extends JpaRepository<StageTemplate, UUID> {

    Optional<StageTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<StageTemplate> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<StageTemplate> findAllByCropCodeAndTenantId(String cropCode, UUID tenantId, Pageable pageable);
}
