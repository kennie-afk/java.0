package com.soko.persistence;

import com.soko.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<LedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
}
