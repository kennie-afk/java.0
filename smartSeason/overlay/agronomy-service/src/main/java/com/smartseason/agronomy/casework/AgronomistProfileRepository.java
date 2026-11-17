package com.smartseason.agronomy.casework;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgronomistProfileRepository extends JpaRepository<AgronomistProfile, UUID> {

    Optional<AgronomistProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<AgronomistProfile> findByTenantIdAndActiveTrue(UUID tenantId);

    List<AgronomistProfile> findByTenantIdOrderByFullNameAsc(UUID tenantId);

    List<AgronomistProfile> findByTenantIdAndUserIdIn(UUID tenantId, java.util.Collection<UUID> userIds);
}
