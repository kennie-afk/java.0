package com.smartseason.weather.repo;

import com.smartseason.weather.domain.WeatherAlertRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeatherAlertRecordRepository extends JpaRepository<WeatherAlertRecord, UUID> {

    Optional<WeatherAlertRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<WeatherAlertRecord> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<WeatherAlertRecord> findAllByGeoCellAndTenantId(String geoCell, UUID tenantId, Pageable pageable);
}
