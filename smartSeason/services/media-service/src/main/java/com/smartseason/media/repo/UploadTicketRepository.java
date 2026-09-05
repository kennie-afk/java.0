package com.smartseason.media.repo;

import com.smartseason.media.domain.UploadTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadTicketRepository extends JpaRepository<UploadTicket, UUID> {

    Optional<UploadTicket> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<UploadTicket> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UploadTicket> findByStorageKeyAndTenantId(String storageKey, UUID tenantId);
}
