package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Page<Tenant> findByLandlordIdOrderByFullNameAsc(UUID landlordId, Pageable pageable);
    Optional<Tenant> findByLandlordIdAndPhone(UUID landlordId, String phone);
    long countByLandlordId(UUID landlordId);

    @Query("""
           SELECT t FROM Tenant t
           WHERE t.landlordId = :landlordId
             AND (LOWER(t.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR t.phone LIKE CONCAT('%', :q, '%')
                  OR LOWER(COALESCE(t.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY t.fullName ASC
           """)
    Page<Tenant> search(@Param("landlordId") UUID landlordId, @Param("q") String q, Pageable pageable);
}
