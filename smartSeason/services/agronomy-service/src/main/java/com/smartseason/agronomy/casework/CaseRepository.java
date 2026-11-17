package com.smartseason.agronomy.casework;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseRepository extends JpaRepository<AdvisoryCase, UUID> {

    Optional<AdvisoryCase> findByIdAndTenantId(UUID id, UUID tenantId);

    // ---- the queues: most urgent first, oldest first within a level, read from the index in order ----
    //
    // Native SQL with the status as a literal, on purpose: the matching indexes are partial
    // (WHERE status = 'OPEN' ...), and a bound status parameter can push Postgres into a generic
    // plan that cannot prove the index applies. See V4__advisory_cases.sql.

    @Query(value = "SELECT * FROM advisory_cases WHERE tenant_id = :t AND status = 'OPEN' "
            + "ORDER BY priority DESC, created_at ASC, id ASC", nativeQuery = true)
    Slice<AdvisoryCase> openFirst(@Param("t") UUID tenantId, Pageable pageable);

    @Query(value = "SELECT * FROM advisory_cases WHERE tenant_id = :t AND status = 'OPEN' AND "
            + "(priority < :p OR (priority = :p AND (created_at > :at OR (created_at = :at AND id > :id)))) "
            + "ORDER BY priority DESC, created_at ASC, id ASC", nativeQuery = true)
    Slice<AdvisoryCase> openAfter(@Param("t") UUID tenantId, @Param("p") int priority, @Param("at") Instant at,
                                  @Param("id") UUID id, Pageable pageable);

    @Query(value = "SELECT * FROM advisory_cases WHERE tenant_id = :t AND status = 'ASSIGNED' AND assigned_to = :u "
            + "ORDER BY priority DESC, created_at ASC, id ASC", nativeQuery = true)
    Slice<AdvisoryCase> assignedFirst(@Param("t") UUID tenantId, @Param("u") UUID assignee, Pageable pageable);

    @Query(value = "SELECT * FROM advisory_cases WHERE tenant_id = :t AND status = 'ASSIGNED' AND assigned_to = :u AND "
            + "(priority < :p OR (priority = :p AND (created_at > :at OR (created_at = :at AND id > :id)))) "
            + "ORDER BY priority DESC, created_at ASC, id ASC", nativeQuery = true)
    Slice<AdvisoryCase> assignedAfter(@Param("t") UUID tenantId, @Param("u") UUID assignee, @Param("p") int priority,
                                      @Param("at") Instant at, @Param("id") UUID id, Pageable pageable);

    @Query(value = "SELECT * FROM advisory_cases WHERE tenant_id = :t AND status = 'ANSWERED' AND assigned_to = :u "
            + "ORDER BY priority DESC, created_at ASC, id ASC", nativeQuery = true)
    Slice<AdvisoryCase> answeredFirst(@Param("t") UUID tenantId, @Param("u") UUID assignee, Pageable pageable);

    @Query(value = "SELECT * FROM advisory_cases WHERE tenant_id = :t AND status = 'ANSWERED' AND assigned_to = :u AND "
            + "(priority < :p OR (priority = :p AND (created_at > :at OR (created_at = :at AND id > :id)))) "
            + "ORDER BY priority DESC, created_at ASC, id ASC", nativeQuery = true)
    Slice<AdvisoryCase> answeredAfter(@Param("t") UUID tenantId, @Param("u") UUID assignee, @Param("p") int priority,
                                      @Param("at") Instant at, @Param("id") UUID id, Pageable pageable);

    // ---- a person's own questions, newest first ----

    Slice<AdvisoryCase> findByTenantIdAndRaisedByOrderByCreatedAtDescIdDesc(UUID tenantId, UUID raisedBy,
                                                                          Pageable pageable);

    @Query("SELECT c FROM AdvisoryCase c WHERE c.tenantId = :t AND c.raisedBy = :u AND "
            + "(c.createdAt < :at OR (c.createdAt = :at AND c.id < :id)) ORDER BY c.createdAt DESC, c.id DESC")
    Slice<AdvisoryCase> mineAfter(@Param("t") UUID tenantId, @Param("u") UUID raisedBy,
                                  @Param("at") Instant at, @Param("id") UUID id, Pageable pageable);

    // ---- counts for the badges (literal statuses, same reason as above) ----

    @Query(value = "SELECT COUNT(*) FROM advisory_cases WHERE tenant_id = :t AND status = 'OPEN'", nativeQuery = true)
    long countOpen(@Param("t") UUID tenantId);

    @Query(value = "SELECT COUNT(*) FROM advisory_cases WHERE tenant_id = :t AND status = 'ASSIGNED' "
            + "AND assigned_to = :u", nativeQuery = true)
    long countAssignedTo(@Param("t") UUID tenantId, @Param("u") UUID assignee);

    @Query(value = "SELECT COUNT(*) FROM advisory_cases WHERE tenant_id = :t AND status IN ('OPEN', 'ASSIGNED') "
            + "AND due_at < :now", nativeQuery = true)
    long countOverdue(@Param("t") UUID tenantId, @Param("now") Instant now);

    long countByTenantIdAndRaisedByAndFarmerUnreadTrue(UUID tenantId, UUID raisedBy);

    @Query(value = "SELECT COUNT(*) FROM advisory_cases WHERE tenant_id = :t AND status = 'ASSIGNED' "
            + "AND assigned_to = :u AND agronomist_unread = TRUE", nativeQuery = true)
    long countUnreadAssignedTo(@Param("t") UUID tenantId, @Param("u") UUID assignee);

    @Query(value = "SELECT COUNT(*) FROM advisory_cases WHERE tenant_id = :t AND id <> :id AND county = :county "
            + "AND crop_code = :crop AND status IN ('OPEN', 'ASSIGNED')", nativeQuery = true)
    long countSimilarOpen(@Param("t") UUID tenantId, @Param("id") UUID id, @Param("county") String county,
                          @Param("crop") String crop);

    /** Cases in hand per agronomist, for routing. */
    @Query(value = "SELECT assigned_to, COUNT(*) FROM advisory_cases WHERE tenant_id = :t AND status = 'ASSIGNED' "
            + "GROUP BY assigned_to", nativeQuery = true)
    List<Object[]> openByAssignee(@Param("t") UUID tenantId);

    /**
     * Takes an unassigned case. The condition is in the UPDATE itself, so when two agronomists
     * press Claim at once the database lets exactly one succeed; the other gets 0 rows.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE advisory_cases SET status = 'ASSIGNED', assigned_to = :u, assigned_at = :now, "
            + "updated_at = :now, version = version + 1 WHERE id = :id AND tenant_id = :t AND status = 'OPEN'",
            nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("t") UUID tenantId, @Param("u") UUID user, @Param("now") Instant now);

    // ---- the organisation-wide list for an owner, newest first ----

    Slice<AdvisoryCase> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT c FROM AdvisoryCase c WHERE c.tenantId = :t AND "
            + "(c.createdAt < :at OR (c.createdAt = :at AND c.id < :id)) ORDER BY c.createdAt DESC, c.id DESC")
    Slice<AdvisoryCase> orgAfter(@Param("t") UUID tenantId, @Param("at") Instant at, @Param("id") UUID id,
                                 Pageable pageable);
}
