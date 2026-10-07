package com.soko.persistence;

import com.soko.domain.PlatformCommission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
public interface PlatformCommissionRepository extends JpaRepository<PlatformCommission, UUID> {

    Optional<PlatformCommission> findByOrderId(UUID orderId);

    List<PlatformCommission> findByTenantIdAndStatusAndCreatedAtBetween(
            UUID tenantId, String status, Instant from, Instant to);

    @Transactional
    @Modifying
    @Query("update PlatformCommission c set c.status = 'INVOICED', c.invoiceId = :invoiceId "
            + "where c.id in :ids")
    int markInvoiced(@Param("ids") List<UUID> ids, @Param("invoiceId") UUID invoiceId);
}
