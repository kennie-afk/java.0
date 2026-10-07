package com.soko.persistence;

import com.soko.domain.OrderLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {

    List<OrderLine> findByOrderId(UUID orderId);

    List<OrderLine> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<OrderLine> findByIdAndSupplierId(UUID id, UUID supplierId);

    @Query(value = """
            select l.id, p.name, o.reference, c.name, c.county,
                   l.quantity, l.unit_cost_cents, l.status, l.created_at, l.tracking_note
              from order_lines l
              join products  p on p.id = l.product_id
              join orders    o on o.id = l.order_id
              join customers c on c.id = o.customer_id
             where l.supplier_id = :supplierId
               and (cast(:status as varchar) is null or l.status = cast(:status as varchar))
             order by l.created_at desc
             limit :max
            """, nativeQuery = true)
    List<Object[]> fulfilmentsForSupplier(
            @Param("supplierId") UUID supplierId, @Param("status") String status, @Param("max") int max);

    /** Counts per status and what is still owed, computed in SQL rather than from a capped page. */
    @Query(value = """
            select count(*) filter (where l.status = 'ROUTED'),
                   count(*) filter (where l.status = 'DISPATCHED'),
                   count(*) filter (where l.status = 'DELIVERED'),
                   coalesce(sum(l.unit_cost_cents * l.quantity) filter (where l.status in ('ROUTED', 'DISPATCHED')), 0)
              from order_lines l
             where l.supplier_id = :supplierId
            """, nativeQuery = true)
    Object[] fulfilmentSummary(@Param("supplierId") UUID supplierId);
}
