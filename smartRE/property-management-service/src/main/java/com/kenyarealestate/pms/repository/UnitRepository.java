package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.Unit;
import com.kenyarealestate.pms.entity.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    Page<Unit> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId, Pageable pageable);
    Page<Unit> findByLandlordIdAndStatusOrderByCreatedAtDesc(UUID landlordId, UnitStatus status, Pageable pageable);
    List<Unit> findByPropertyIdOrderByLabelAsc(UUID propertyId);
    long countByLandlordId(UUID landlordId);
    long countByLandlordIdAndStatus(UUID landlordId, UnitStatus status);
    boolean existsByPropertyIdAndLabelIgnoreCase(UUID propertyId, String label);
}
