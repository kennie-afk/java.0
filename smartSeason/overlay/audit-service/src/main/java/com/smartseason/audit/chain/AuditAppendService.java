package com.smartseason.audit.chain;

import com.smartseason.audit.domain.AuditRecord;
import com.smartseason.audit.platform.TenantContext;
import com.smartseason.audit.repo.AuditChainRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends entries to the tamper-evident audit chain.
 *
 * Sequence numbers and hashes are assigned here, never accepted from the
 * caller: a log whose writer chooses its own position and hash proves nothing.
 *
 * Appends for one tenant are serialised by a Postgres advisory lock held for the
 * length of the transaction. Without it, two writers read the same "last"
 * sequence and both claim the next one - measured at 20 concurrent appends
 * yielding 9 distinct sequences, which breaks verification. The unique index on
 * (tenant_id, sequence) is the real guarantee; the lock is what stops writers
 * colliding and having to retry. Chains are per tenant, so this serialises one
 * organisation's writes and never the whole service.
 */
@Service
public class AuditAppendService {

    private final AuditChainRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditAppendService(AuditChainRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditRecord append(AppendRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        lockTenant(tenantId);

        AuditRecord previous =
                repository.findFirstByTenantIdOrderBySequenceDesc(tenantId).orElse(null);
        String previousHash = previous == null ? HashChain.GENESIS : previous.getRecordHash();
        long sequence = (previous == null ? 0L : previous.getSequence()) + 1;

        // Truncated to microseconds because that is all Postgres TIMESTAMPTZ keeps.
        // Hashing a nanosecond value the database then rounds would make every
        // record fail its own verification the moment it was read back.
        Instant occurredAt = (request.occurredAt() == null ? Instant.now() : request.occurredAt())
                .truncatedTo(ChronoUnit.MICROS);
        String outcome = request.outcome() == null ? "SUCCESS" : request.outcome();

        AuditEntry entry = new AuditEntry(
                sequence, request.serviceName(), request.actorUserId(), request.actorRole(),
                request.action(), request.resourceType(), request.resourceId(),
                outcome, occurredAt, request.ipAddress(), request.details());

        AuditRecord record = new AuditRecord();
        record.setTenantId(tenantId);
        record.setSequence(sequence);
        record.setServiceName(request.serviceName());
        record.setActorUserId(request.actorUserId());
        record.setActorRole(request.actorRole());
        record.setAction(request.action());
        record.setResourceType(request.resourceType());
        record.setResourceId(request.resourceId());
        record.setOutcome(AuditRecord.Outcome.valueOf(outcome));
        record.setOccurredAt(occurredAt);
        record.setIpAddress(request.ipAddress());
        record.setUserAgent(request.userAgent());
        record.setDetails(request.details());
        record.setPreviousHash(previousHash);
        record.setRecordHash(HashChain.hash(entry, previousHash));

        return repository.save(record);
    }

    /** Serialises appends for one tenant until the transaction ends. */
    private void lockTenant(UUID tenantId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(cast(:tenant as text), 0))")
                .setParameter("tenant", tenantId.toString())
                .getSingleResult();
    }

    /** Recomputes every hash in the tenant's chain and reports the first break. */
    @Transactional(readOnly = true)
    public HashChain.Verification verify() {
        List<AuditRecord> records =
                repository.findAllByTenantIdOrderBySequenceAsc(TenantContext.requireTenantId());

        List<AuditEntry> entries = new ArrayList<>(records.size());
        List<String> hashes = new ArrayList<>(records.size());
        List<String> previous = new ArrayList<>(records.size());

        for (AuditRecord record : records) {
            entries.add(new AuditEntry(
                    record.getSequence(), record.getServiceName(), record.getActorUserId(),
                    record.getActorRole(), record.getAction(), record.getResourceType(),
                    record.getResourceId(), record.getOutcome().name(), record.getOccurredAt(),
                    record.getIpAddress(), record.getDetails()));
            hashes.add(record.getRecordHash());
            previous.add(record.getPreviousHash());
        }

        return HashChain.verify(entries, hashes, previous);
    }

    @Transactional(readOnly = true)
    public List<AuditRecord> forResource(String resourceId) {
        return repository.findAllByTenantIdAndResourceIdOrderBySequenceAsc(
                TenantContext.requireTenantId(), resourceId);
    }
}
