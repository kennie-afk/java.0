package com.soko.persistence;

import com.soko.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<LedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
}
