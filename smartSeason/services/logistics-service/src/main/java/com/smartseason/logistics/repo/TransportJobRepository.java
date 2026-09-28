package com.smartseason.logistics.repo;

import com.smartseason.logistics.domain.TransportJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransportJobRepository extends JpaRepository<TransportJob, UUID> {

    Optional<TransportJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TransportJob> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TransportJob> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM TransportJob e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<TransportJob> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<TransportJob> findByJobNumberAndTenantId(String jobNumber, UUID tenantId);
    Page<TransportJob> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
    Page<TransportJob> findAllByBatchIdAndTenantId(UUID batchId, UUID tenantId, Pageable pageable);
    Page<TransportJob> findAllByVehicleIdAndTenantId(UUID vehicleId, UUID tenantId, Pageable pageable);
    Page<TransportJob> findAllByDriverIdAndTenantId(UUID driverId, UUID tenantId, Pageable pageable);
}
