package com.smartseason.logistics.repo;

import com.smartseason.logistics.domain.RouteStop;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {

    Optional<RouteStop> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<RouteStop> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<RouteStop> findAllByTransportJobIdAndTenantId(UUID transportJobId, UUID tenantId, Pageable pageable);
}
