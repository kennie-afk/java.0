package com.smartseason.logistics.repo;

import com.smartseason.logistics.domain.ProofOfDelivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProofOfDeliveryRepository extends JpaRepository<ProofOfDelivery, UUID> {

    Optional<ProofOfDelivery> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ProofOfDelivery> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ProofOfDelivery> findByTransportJobIdAndTenantId(UUID transportJobId, UUID tenantId);
}
