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

    @Query(value = """
            select w.id, p.name, s.name, w.quantity, w.reason, w.value_cents, w.recorded_at
              from wastage_records w
              join products p on p.id = w.product_id
              join suppliers s on s.id = w.supplier_id
             where w.tenant_id = :tenantId
             order by w.recorded_at desc
             limit :max
            """, nativeQuery = true)
    List<Object[]> listDetailed(@Param("tenantId") UUID tenantId, @Param("max") int max);
}
