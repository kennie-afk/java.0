package com.smartseason.weather.repo;

import com.smartseason.weather.domain.WeatherStation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeatherStationRepository extends JpaRepository<WeatherStation, UUID> {

    Optional<WeatherStation> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<WeatherStation> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<WeatherStation> findByExternalIdAndTenantId(String externalId, UUID tenantId);
    Page<WeatherStation> findAllByCountyAndTenantId(String county, UUID tenantId, Pageable pageable);
}
