package com.soko.persistence;

import com.soko.domain.OrderLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
             order by l.created_at desc
             limit :max
            """, nativeQuery = true)
    List<Object[]> fulfilmentsForSupplier(
            @Param("supplierId") UUID supplierId, @Param("max") int max);
}
