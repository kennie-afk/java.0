package com.soko.persistence;

import com.soko.domain.SalesOrder;
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
public interface OrderRepository extends JpaRepository<SalesOrder, UUID> {

    List<SalesOrder> findByTenantIdOrderByPlacedAtDesc(UUID tenantId, Pageable pageable);

    Optional<SalesOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SalesOrder> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    long countByTenantId(UUID tenantId);

    List<SalesOrder> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    Optional<SalesOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query(value = """
            select count(*) filter (where o.status <> 'CANCELLED') as orders,
                   coalesce(sum(o.revenue_cents) filter (where o.status <> 'CANCELLED'), 0) as revenue,
                   coalesce(sum(o.margin_cents) filter (where o.status <> 'CANCELLED'), 0)  as margin,
                   (select count(*) from suppliers s where s.tenant_id = :tenantId) as suppliers,
                   (select count(*) from products p where p.tenant_id = :tenantId)  as products,
                   (select count(*) from customers c where c.tenant_id = :tenantId) as customers
              from orders o
             where o.tenant_id = :tenantId
            """, nativeQuery = true)
    Object[] summarise(@Param("tenantId") UUID tenantId);

    @Query(value = """
            select o.id, o.reference, c.name, c.county, o.status,
                   o.revenue_cents, o.cost_cents, o.margin_cents, o.placed_at
              from orders o
              join customers c on c.id = o.customer_id
             where o.tenant_id = :tenantId
               and (lower(o.reference) like :pattern or lower(c.name) like :pattern)
             order by o.placed_at desc, o.id
             limit :max offset :offset
            """, nativeQuery = true)
    List<Object[]> listWithCustomer(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern,
            @Param("max") int max, @Param("offset") long offset);

    @Query(value = """
            select count(*) from orders o join customers c on c.id = o.customer_id
             where o.tenant_id = :tenantId
               and (lower(o.reference) like :pattern or lower(c.name) like :pattern)
            """, nativeQuery = true)
    long countWithCustomer(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern);

    /** One row per week: live orders, revenue, margin, and how many were cancelled. */
    @Query(value = """
            select date_trunc('week', o.placed_at) as week,
                   count(*) filter (where o.status <> 'CANCELLED') as orders,
                   coalesce(sum(o.revenue_cents) filter (where o.status <> 'CANCELLED'), 0) as revenue,
                   coalesce(sum(o.margin_cents) filter (where o.status <> 'CANCELLED'), 0) as margin,
                   count(*) filter (where o.status = 'CANCELLED') as cancelled
              from orders o
             where o.tenant_id = :tenantId and o.placed_at >= :since
             group by 1
             order by 1
            """, nativeQuery = true)
    List<Object[]> weekly(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
