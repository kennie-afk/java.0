package com.smartseason.ledger.repo;

import com.smartseason.ledger.domain.JournalEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<JournalEntry> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<JournalEntry> findByEntryNumberAndTenantId(String entryNumber, UUID tenantId);

    Page<JournalEntry> findAllBySourceRefAndTenantId(String sourceRef, UUID tenantId, Pageable pageable);

    Optional<JournalEntry> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
