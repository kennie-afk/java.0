package com.smartseason.marketplace.platform;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    List<OutboxEntry> findAllByStatusOrderByCreatedAtAsc(OutboxEntry.Status status, Pageable pageable);

    long countByStatus(OutboxEntry.Status status);
}
