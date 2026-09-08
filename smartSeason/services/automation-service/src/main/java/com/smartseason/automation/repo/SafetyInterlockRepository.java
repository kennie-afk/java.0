package com.smartseason.automation.repo;

import com.smartseason.automation.domain.SafetyInterlock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SafetyInterlockRepository extends JpaRepository<SafetyInterlock, UUID> {

    Optional<SafetyInterlock> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<SafetyInterlock> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<SafetyInterlock> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
}
