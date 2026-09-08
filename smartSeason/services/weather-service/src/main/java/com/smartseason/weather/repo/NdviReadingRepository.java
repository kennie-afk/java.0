package com.smartseason.weather.repo;

import com.smartseason.weather.domain.NdviReading;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NdviReadingRepository extends JpaRepository<NdviReading, UUID> {

    Optional<NdviReading> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<NdviReading> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<NdviReading> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<NdviReading> findAllByGeoCellAndTenantId(String geoCell, UUID tenantId, Pageable pageable);
}
