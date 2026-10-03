package com.smartseason.payout.repo;

import com.smartseason.payout.domain.Settlement;
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
public interface SettlementRepository extends JpaRepository<Settlement, UUID>, JpaSpecificationExecutor<Settlement> {

    Optional<Settlement> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Settlement> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Settlement> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM Settlement e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<Settlement> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<Settlement> findBySettlementNumberAndTenantId(String settlementNumber, UUID tenantId);
    Page<Settlement> findAllByPayeeOrgIdAndTenantId(UUID payeeOrgId, UUID tenantId, Pageable pageable);
    Page<Settlement> findAllByPayeeUserIdAndTenantId(UUID payeeUserId, UUID tenantId, Pageable pageable);
    Page<Settlement> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
}
