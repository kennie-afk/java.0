package com.soko.persistence;

import com.soko.domain.Invoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByIssuedAtDesc(UUID tenantId, Pageable pageable);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPeriodStartAndPeriodEnd(UUID tenantId, Instant periodStart, Instant periodEnd);

    /**
     * A transaction-scoped advisory lock keyed on (tenant, period). Two replicas firing the monthly
     * job at the same moment cannot both pass the "already invoiced?" check: the loser gets false.
     */
    @Query(value = "select pg_try_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
    boolean tryLockPeriod(@Param("key") String key);
}
