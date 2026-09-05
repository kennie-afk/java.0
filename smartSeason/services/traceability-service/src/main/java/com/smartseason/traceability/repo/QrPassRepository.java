package com.smartseason.traceability.repo;

import com.smartseason.traceability.domain.QrPass;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrPassRepository extends JpaRepository<QrPass, UUID> {

    Optional<QrPass> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<QrPass> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<QrPass> findAllByBatchCodeAndTenantId(String batchCode, UUID tenantId, Pageable pageable);
    Optional<QrPass> findByPassCodeAndTenantId(String passCode, UUID tenantId);
}
