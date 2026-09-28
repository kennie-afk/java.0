package com.smartseason.payout.repo;

import com.smartseason.payout.domain.PayoutHold;
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
public interface PayoutHoldRepository extends JpaRepository<PayoutHold, UUID> {

    Optional<PayoutHold> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutHold> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutHold> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PayoutHold e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PayoutHold> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<PayoutHold> findAllByPayoutItemIdAndTenantId(UUID payoutItemId, UUID tenantId, Pageable pageable);
    Page<PayoutHold> findAllByPayeeIdAndTenantId(UUID payeeId, UUID tenantId, Pageable pageable);
    Page<PayoutHold> findAllByFraudCaseIdAndTenantId(UUID fraudCaseId, UUID tenantId, Pageable pageable);
}
