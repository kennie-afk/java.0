package com.soko.persistence;

import com.soko.domain.Offer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByTenantIdOrderByCostCentsAsc(UUID tenantId);

    List<Offer> findByTenantIdOrderByCostCentsAsc(UUID tenantId, Pageable pageable);

    /** Offers whose product or supplier name matches; cheapest first, id as the tiebreak. */
    @Query(value = "select o from Offer o, Product p, Supplier s where o.tenantId = :tenantId "
            + "and p.id = o.productId and s.id = o.supplierId "
            + "and (lower(p.name) like :pattern escape '\\' or lower(s.name) like :pattern escape '\\') "
            + "order by o.costCents, o.id",
            countQuery = "select count(o) from Offer o, Product p, Supplier s where o.tenantId = :tenantId "
            + "and p.id = o.productId and s.id = o.supplierId "
            + "and (lower(p.name) like :pattern escape '\\' or lower(s.name) like :pattern escape '\\')")
    Page<Offer> search(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);

    Optional<Offer> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Offer> findBySupplierIdOrderByCostCentsAsc(UUID supplierId);

    Optional<Offer> findByIdAndSupplierId(UUID id, UUID supplierId);

    Optional<Offer> findBySupplierIdAndProductId(UUID supplierId, UUID productId);

    @Query("select o from Offer o where o.tenantId = :tenantId and o.productId = :productId "
            + "and o.status = 'ACTIVE' and o.availableQty >= :quantity order by o.costCents asc")
    List<Offer> candidates(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId,
            @Param("quantity") int quantity);

    @Transactional
    @Modifying
    @Query(value = "update offers set available_qty = available_qty - :quantity "
            + "where id = :id and available_qty >= :quantity", nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("quantity") int quantity);

    @Transactional
    @Modifying
    @Query(value = "update offers set available_qty = available_qty + :quantity where id = :id",
            nativeQuery = true)
    int restock(@Param("id") UUID id, @Param("quantity") int quantity);

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
                   coalesce(sum(o.available_qty), 0) as in_stock, p.photo_url
              from products p
              left join offers o
                     on o.product_id = p.id and o.status = 'ACTIVE'
             where p.tenant_id = :tenantId
               and (lower(p.name) like :pattern or lower(p.category) like :pattern)
             group by p.id
            having coalesce(sum(o.available_qty), 0) > 0
             order by p.category, p.name, p.id
             limit :limit offset :offset
            """, nativeQuery = true)
    List<Object[]> storefront(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern,
            @Param("limit") int limit, @Param("offset") long offset);

    /** How many products {@link #storefront} would return without paging: in stock and matching. */
    @Query(value = """
            select count(*) from (
              select p.id
                from products p
                left join offers o on o.product_id = p.id and o.status = 'ACTIVE'
               where p.tenant_id = :tenantId
                 and (lower(p.name) like :pattern or lower(p.category) like :pattern)
               group by p.id
              having coalesce(sum(o.available_qty), 0) > 0) in_stock
            """, nativeQuery = true)
    long countStorefront(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern);
}
