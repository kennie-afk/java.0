package com.kenyarealestate.property.repository;

import com.kenyarealestate.property.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Page<Property> findBySellerId(UUID sellerId, Pageable p);
    List<Property> findBySellerIdAndStatus(UUID sellerId, ListingStatus status);
    Page<Property> findBySellerIdAndStatus(UUID sellerId, ListingStatus status, Pageable p);
    List<Property> findByParcelNumberAndIdNot(String parcelNumber, UUID id);
    Page<Property> findByStatus(ListingStatus status, Pageable p);

    @Modifying
    @Query("UPDATE Property p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    long countByStatus(ListingStatus status);

    @Query("SELECT COALESCE(AVG(p.price), 0) FROM Property p WHERE p.status = 'ACTIVE'")
    BigDecimal averageActivePrice();

    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Property p")
    long sumViewCount();

    @Query("SELECT p.propertyType, COUNT(p) FROM Property p WHERE p.status = 'ACTIVE' GROUP BY p.propertyType")
    List<Object[]> countActiveByType();

    @Query("SELECT p.county, COUNT(p) FROM Property p WHERE p.status = 'ACTIVE' GROUP BY p.county ORDER BY COUNT(p) DESC")
    List<Object[]> countActiveByCounty(Pageable p);

    @Query(value =
            "SELECT * FROM properties p WHERE p.status = 'ACTIVE'" +
                    " AND (:county IS NULL OR LOWER(p.county) = LOWER(:county))" +
                    " AND (:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%',:city,'%')))" +
                    " AND (CAST(:type AS VARCHAR) IS NULL OR p.property_type = CAST(:type AS VARCHAR))" +
                    " AND (CAST(:lt AS VARCHAR) IS NULL OR p.listing_type = CAST(:lt AS VARCHAR))" +
                    " AND (:minPrice IS NULL OR p.price >= CAST(:minPrice AS NUMERIC))" +
                    " AND (:maxPrice IS NULL OR p.price <= CAST(:maxPrice AS NUMERIC))" +
                    " AND (:minBed IS NULL OR p.bedrooms >= :minBed)" +
                    " AND (:kw IS NULL OR (LOWER(p.title) LIKE LOWER(CONCAT('%',:kw,'%'))" +
                    "     OR LOWER(p.description) LIKE LOWER(CONCAT('%',:kw,'%'))))" +
                    " AND (:verifiedOnly = FALSE OR (p.seller_identity_verified = TRUE AND p.property_ownership_verified = TRUE))" +
                    // Without an ORDER BY, Postgres may return the same rows in a different order on every page,
                    // so a visitor paging through results could skip or repeat listings. The CASE terms apply the
                    // one whitelisted sort the service passes in; the last two terms make any order total.
                    " ORDER BY" +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'price' AND :asc = TRUE THEN p.price END ASC," +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'price' AND :asc = FALSE THEN p.price END DESC," +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'bedrooms' AND :asc = TRUE THEN p.bedrooms END ASC NULLS LAST," +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'bedrooms' AND :asc = FALSE THEN p.bedrooms END DESC NULLS LAST," +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'viewCount' AND :asc = TRUE THEN p.view_count END ASC," +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'viewCount' AND :asc = FALSE THEN p.view_count END DESC," +
                    " CASE WHEN CAST(:sortKey AS VARCHAR) = 'createdAt' AND :asc = TRUE THEN p.created_at END ASC," +
                    " p.created_at DESC, p.id DESC",
            countQuery =
                    "SELECT COUNT(*) FROM properties p WHERE p.status = 'ACTIVE'" +
                            " AND (:county IS NULL OR LOWER(p.county) = LOWER(:county))" +
                            " AND (:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%',:city,'%')))" +
                            " AND (CAST(:type AS VARCHAR) IS NULL OR p.property_type = CAST(:type AS VARCHAR))" +
                            " AND (CAST(:lt AS VARCHAR) IS NULL OR p.listing_type = CAST(:lt AS VARCHAR))" +
                            " AND (:minPrice IS NULL OR p.price >= CAST(:minPrice AS NUMERIC))" +
                            " AND (:maxPrice IS NULL OR p.price <= CAST(:maxPrice AS NUMERIC))" +
                            " AND (:minBed IS NULL OR p.bedrooms >= :minBed)" +
                            " AND (:kw IS NULL OR (LOWER(p.title) LIKE LOWER(CONCAT('%',:kw,'%'))" +
                            "     OR LOWER(p.description) LIKE LOWER(CONCAT('%',:kw,'%'))))" +
                            " AND (:verifiedOnly = FALSE OR (p.seller_identity_verified = TRUE AND p.property_ownership_verified = TRUE))",
            nativeQuery = true)
    Page<Property> search(
            @Param("county") String county,
            @Param("city") String city,
            @Param("type") String type,
            @Param("lt") String lt,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minBed") Integer minBed,
            @Param("kw") String kw,
            @Param("verifiedOnly") boolean verifiedOnly,
            @Param("sortKey") String sortKey,
            @Param("asc") boolean asc,
            Pageable p);
}
