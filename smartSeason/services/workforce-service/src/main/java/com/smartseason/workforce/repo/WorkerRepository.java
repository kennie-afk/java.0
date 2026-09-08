package com.smartseason.workforce.repo;

import com.smartseason.workforce.domain.Worker;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<Worker> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Worker> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Worker> findAllByUserIdAndTenantId(UUID userId, UUID tenantId, Pageable pageable);
    Page<Worker> findAllByNationalIdAndTenantId(String nationalId, UUID tenantId, Pageable pageable);
    Page<Worker> findAllByPhoneAndTenantId(String phone, UUID tenantId, Pageable pageable);
    Page<Worker> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
