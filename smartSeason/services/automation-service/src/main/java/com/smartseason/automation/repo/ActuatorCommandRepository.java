package com.smartseason.automation.repo;

import com.smartseason.automation.domain.ActuatorCommand;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActuatorCommandRepository extends JpaRepository<ActuatorCommand, UUID> {

    Optional<ActuatorCommand> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ActuatorCommand> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ActuatorCommand> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
    Page<ActuatorCommand> findAllByRuleIdAndTenantId(UUID ruleId, UUID tenantId, Pageable pageable);
    Optional<ActuatorCommand> findByCommandKeyAndTenantId(String commandKey, UUID tenantId);
}
