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
