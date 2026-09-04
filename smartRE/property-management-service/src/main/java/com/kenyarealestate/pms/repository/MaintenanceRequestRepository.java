package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.MaintenanceRequest;
import com.kenyarealestate.pms.entity.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, UUID> {
    Page<MaintenanceRequest> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId, Pageable pageable);
    Page<MaintenanceRequest> findByLandlordIdAndStatusOrderByCreatedAtDesc(UUID landlordId, MaintenanceStatus status, Pageable pageable);
    Page<MaintenanceRequest> findByTenantIdInOrderByCreatedAtDesc(List<UUID> tenantIds, Pageable pageable);
    long countByLandlordIdAndStatusIn(UUID landlordId, List<MaintenanceStatus> statuses);
}
