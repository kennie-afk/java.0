package com.smartseason.logistics.repo;

import com.smartseason.logistics.domain.ColdChainReading;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ColdChainReadingRepository extends JpaRepository<ColdChainReading, UUID> {

    Optional<ColdChainReading> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ColdChainReading> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ColdChainReading> findAllByTransportJobIdAndTenantId(UUID transportJobId, UUID tenantId, Pageable pageable);
}
