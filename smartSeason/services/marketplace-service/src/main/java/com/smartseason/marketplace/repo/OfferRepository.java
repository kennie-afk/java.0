package com.smartseason.marketplace.repo;

import com.smartseason.marketplace.domain.Offer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Offer> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Offer> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM Offer e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<Offer> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<Offer> findAllByListingIdAndTenantId(UUID listingId, UUID tenantId, Pageable pageable);
    Page<Offer> findAllByDemandPostIdAndTenantId(UUID demandPostId, UUID tenantId, Pageable pageable);
    Page<Offer> findAllByFromOrgIdAndTenantId(UUID fromOrgId, UUID tenantId, Pageable pageable);
    Page<Offer> findAllByToOrgIdAndTenantId(UUID toOrgId, UUID tenantId, Pageable pageable);
}
