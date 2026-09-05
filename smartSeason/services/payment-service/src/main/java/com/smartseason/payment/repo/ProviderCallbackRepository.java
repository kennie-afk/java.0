package com.smartseason.payment.repo;

import com.smartseason.payment.domain.ProviderCallback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderCallbackRepository extends JpaRepository<ProviderCallback, UUID> {

    Optional<ProviderCallback> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ProviderCallback> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ProviderCallback> findAllByProviderAndTenantId(String provider, UUID tenantId, Pageable pageable);
    Page<ProviderCallback> findAllByExternalRefAndTenantId(String externalRef, UUID tenantId, Pageable pageable);
}
