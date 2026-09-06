package com.soko.persistence;

import com.soko.domain.Offer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByTenantIdOrderByCostCentsAsc(UUID tenantId);

    Optional<Offer> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Offer> findBySupplierIdOrderByCostCentsAsc(UUID supplierId);

    Optional<Offer> findByIdAndSupplierId(UUID id, UUID supplierId);

    @Query("select o from Offer o where o.tenantId = :tenantId and o.productId = :productId "
            + "and o.status = 'ACTIVE' and o.availableQty >= :quantity order by o.costCents asc")
    List<Offer> candidates(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId,
            @Param("quantity") int quantity);

    @Modifying
    @Query(value = "update offers set available_qty = available_qty - :quantity "
            + "where id = :id and available_qty >= :quantity", nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("quantity") int quantity);

    @Query(value = """
            select o.id, p.name, p.sku, p.unit, p.category, o.cost_cents,
                   o.available_qty, o.status, p.list_price_cents,
                   p.requires_cold_chain, p.shelf_life_hours
              from offers o
              join products p on p.id = o.product_id
             where o.supplier_id = :supplierId
             order by p.category, p.name
            """, nativeQuery = true)
    List<Object[]> offersForSupplier(@Param("supplierId") UUID supplierId);

    @Query(value = """
            select p.id, p.sku, p.name, p.category, p.unit, p.perishable,
                   p.requires_cold_chain, p.shelf_life_hours, p.list_price_cents,
                   coalesce(sum(o.available_qty), 0) as in_stock
              from products p
              left join offers o
                     on o.product_id = p.id and o.status = 'ACTIVE'
             where p.tenant_id = :tenantId
             group by p.id
             order by p.category, p.name
            """, nativeQuery = true)
    List<Object[]> storefront(@Param("tenantId") UUID tenantId);
}
