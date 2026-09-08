package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.Unit;
import com.kenyarealestate.pms.entity.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    Page<Unit> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId, Pageable pageable);
    Page<Unit> findByLandlordIdAndStatusOrderByCreatedAtDesc(UUID landlordId, UnitStatus status, Pageable pageable);
    List<Unit> findByPropertyIdOrderByLabelAsc(UUID propertyId);
    long countByLandlordId(UUID landlordId);
    long countByLandlordIdAndStatus(UUID landlordId, UnitStatus status);
    boolean existsByPropertyIdAndLabelIgnoreCase(UUID propertyId, String label);

    /**
     * One query behind every filter the units list offers, so a landlord with several
     * hundred units pages through the database rather than through a list the browser
     * had to download in full.
     *
     * <p>Each filter is optional and skipped when null — that is what the
     * `:x IS NULL OR` guard does. Writing five finder methods for the combinations, or
     * fetching everything and filtering in memory, are the two alternatives; the first
     * multiplies with every new filter and the second stops working at exactly the scale
     * this exists for.
     *
     * <p>Ordered by label rather than creation date: a landlord looking at Riverside
     * Court wants A1, A2, B1, not whichever unit was typed in last.
     *
     * <p>{@code q} is a ready-made lowercase LIKE pattern and must never be null. It was
     * written as {@code LOWER(CONCAT('%', :q, '%'))} first, which fails on Postgres with
     * "function lower(bytea) does not exist": with a null parameter the driver cannot
     * infer a type, the server guesses bytea, and there is no LOWER for that. Building
     * the pattern in Java sidesteps the inference entirely — "no search" becomes the
     * pattern {@code %}, which matches every row.
     */
    @Query("""
           SELECT u FROM Unit u
           WHERE u.landlordId = :landlordId
             AND (:propertyId IS NULL OR u.propertyId = :propertyId)
             AND (:status     IS NULL OR u.status = :status)
             AND (LOWER(u.label)                 LIKE :q
                  OR LOWER(COALESCE(u.unitType, '')) LIKE :q
                  OR LOWER(COALESCE(u.notes, ''))    LIKE :q)
           ORDER BY u.label ASC
           """)
    Page<Unit> search(@Param("landlordId") UUID landlordId,
                      @Param("propertyId") UUID propertyId,
                      @Param("status") UnitStatus status,
                      @Param("q") String q,
                      Pageable pageable);
}
