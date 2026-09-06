package com.soko.persistence;

import com.soko.domain.SalesOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<SalesOrder, UUID> {

    List<SalesOrder> findByTenantIdOrderByPlacedAtDesc(UUID tenantId, Pageable pageable);

    Optional<SalesOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    List<SalesOrder> findByCustomerIdOrderByPlacedAtDesc(UUID customerId);

    Optional<SalesOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query(value = """
            select count(*)              as orders,
                   coalesce(sum(o.revenue_cents), 0) as revenue,
                   coalesce(sum(o.margin_cents), 0)  as margin,
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
             order by o.placed_at desc
             limit :max
            """, nativeQuery = true)
    List<Object[]> listWithCustomer(@Param("tenantId") UUID tenantId, @Param("max") int max);
}
