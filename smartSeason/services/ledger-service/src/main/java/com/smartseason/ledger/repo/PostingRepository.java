package com.smartseason.ledger.repo;

import com.smartseason.ledger.domain.Posting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostingRepository extends JpaRepository<Posting, UUID> {

    Optional<Posting> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Posting> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Posting> findAllByJournalEntryIdAndTenantId(UUID journalEntryId, UUID tenantId, Pageable pageable);
    Page<Posting> findAllByAccountIdAndTenantId(UUID accountId, UUID tenantId, Pageable pageable);
}
