package com.smartseason.logistics.repo;

import com.smartseason.logistics.domain.TransportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransportJobRepository extends JpaRepository<TransportJob, UUID> {

    Optional<TransportJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<TransportJob> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<TransportJob> findByJobNumberAndTenantId(String jobNumber, UUID tenantId);
    Page<TransportJob> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
    Page<TransportJob> findAllByBatchIdAndTenantId(UUID batchId, UUID tenantId, Pageable pageable);
    Page<TransportJob> findAllByVehicleIdAndTenantId(UUID vehicleId, UUID tenantId, Pageable pageable);
    Page<TransportJob> findAllByDriverIdAndTenantId(UUID driverId, UUID tenantId, Pageable pageable);
}
