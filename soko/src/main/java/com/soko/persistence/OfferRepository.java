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
}
