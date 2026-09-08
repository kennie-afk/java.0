package com.smartseason.search.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    List<OutboxEntry> findAllByStatusOrderByCreatedAtAsc(OutboxEntry.Status status, Pageable pageable);

    long countByStatus(OutboxEntry.Status status);

    @Modifying
    @Query("DELETE FROM OutboxEntry e WHERE e.status = :status AND e.publishedAt < :before")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxEntry.Status status,
                                           @Param("before") Instant before);
}
