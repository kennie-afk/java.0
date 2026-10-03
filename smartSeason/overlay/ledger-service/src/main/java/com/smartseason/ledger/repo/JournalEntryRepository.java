package com.smartseason.ledger.repo;

import com.smartseason.ledger.domain.JournalEntry;
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
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID>, JpaSpecificationExecutor<JournalEntry> {

    Optional<JournalEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<JournalEntry> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<JournalEntry> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM JournalEntry e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<JournalEntry> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<JournalEntry> findByEntryNumberAndTenantId(String entryNumber, UUID tenantId);

    Page<JournalEntry> findAllBySourceRefAndTenantId(String sourceRef, UUID tenantId, Pageable pageable);

    Optional<JournalEntry> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
