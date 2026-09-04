package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {
    Page<Lease> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId, Pageable pageable);
    Page<Lease> findByLandlordIdAndStatusOrderByCreatedAtDesc(UUID landlordId, LeaseStatus status, Pageable pageable);
    Page<Lease> findByTenantIdInOrderByCreatedAtDesc(List<UUID> tenantIds, Pageable pageable);
    Optional<Lease> findByUnitIdAndStatus(UUID unitId, LeaseStatus status);
    List<Lease> findByUnitIdOrderByStartDateDesc(UUID unitId);
    List<Lease> findByStatus(LeaseStatus status);
    boolean existsByTenantId(UUID tenantId);
    long countByLandlordIdAndStatus(UUID landlordId, LeaseStatus status);
}
