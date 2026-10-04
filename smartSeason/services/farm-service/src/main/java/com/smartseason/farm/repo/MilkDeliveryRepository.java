package com.smartseason.farm.repo;

import com.smartseason.farm.domain.MilkDelivery;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MilkDeliveryRepository extends JpaRepository<MilkDelivery, UUID>, JpaSpecificationExecutor<MilkDelivery> {

    Optional<MilkDelivery> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MilkDelivery> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MilkDelivery> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM MilkDelivery e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<MilkDelivery> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<MilkDelivery> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<MilkDelivery> findAllByDeliveredOnAndTenantId(LocalDate deliveredOn, UUID tenantId, Pageable pageable);
    Page<MilkDelivery> findAllByReceiptNoAndTenantId(String receiptNo, UUID tenantId, Pageable pageable);
}
