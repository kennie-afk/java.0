package com.smartseason.catalog.repo;

import com.smartseason.catalog.domain.Certification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    Optional<Certification> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Certification> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Certification> findByCodeAndTenantId(String code, UUID tenantId);
}
