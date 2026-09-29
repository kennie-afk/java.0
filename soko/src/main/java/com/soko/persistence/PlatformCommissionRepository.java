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

public interface PlatformCommissionRepository extends JpaRepository<PlatformCommission, UUID> {

    Optional<PlatformCommission> findByOrderId(UUID orderId);

    List<PlatformCommission> findByTenantIdAndStatusAndCreatedAtBetween(
            UUID tenantId, String status, Instant from, Instant to);

    @Modifying
    @Query("update PlatformCommission c set c.status = 'INVOICED', c.invoiceId = :invoiceId "
            + "where c.id in :ids")
    int markInvoiced(@Param("ids") List<UUID> ids, @Param("invoiceId") UUID invoiceId);
}
