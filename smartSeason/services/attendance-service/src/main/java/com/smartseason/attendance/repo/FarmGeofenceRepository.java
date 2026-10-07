package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.Geofence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The fences a clock-in is judged against: the farm's active ones, inside the caller's tenant. */
public interface FarmGeofenceRepository extends Repository<Geofence, UUID> {

    List<Geofence> findAllByTenantIdAndFarmIdAndActive(UUID tenantId, UUID farmId, Boolean active);
}
