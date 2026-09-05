package com.smartseason.agronomy.repo;

import com.smartseason.agronomy.domain.PestDisease;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PestDiseaseRepository extends JpaRepository<PestDisease, UUID> {

    Optional<PestDisease> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PestDisease> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PestDisease> findByCodeAndTenantId(String code, UUID tenantId);
}
