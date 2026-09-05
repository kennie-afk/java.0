package com.smartseason.identity.repo;

import com.smartseason.identity.domain.KycRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KycRecordRepository extends JpaRepository<KycRecord, UUID> {

    Optional<KycRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<KycRecord> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<KycRecord> findAllBySubjectIdAndTenantId(UUID subjectId, UUID tenantId, Pageable pageable);
}
