package com.soko.persistence;

import com.soko.domain.WastageRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WastageRecordRepository extends JpaRepository<WastageRecord, UUID> {

    List<WastageRecord> findByTenantIdOrderByRecordedAtDesc(UUID tenantId, Pageable pageable);

    @Query("select coalesce(sum(w.valueCents), 0) from WastageRecord w where w.tenantId = :tenantId")
    long totalValueCents(@Param("tenantId") UUID tenantId);
}
