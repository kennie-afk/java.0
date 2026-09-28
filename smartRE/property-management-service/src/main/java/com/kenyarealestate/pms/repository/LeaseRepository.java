package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {
    Page<Lease> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId, Pageable pageable);
    Page<Lease> findByLandlordIdAndStatusOrderByCreatedAtDesc(UUID landlordId, LeaseStatus status, Pageable pageable);
    Page<Lease> findByTenantIdInOrderByCreatedAtDesc(List<UUID> tenantIds, Pageable pageable);
    Optional<Lease> findByUnitIdAndStatus(UUID unitId, LeaseStatus status);
    List<Lease> findByUnitIdOrderByStartDateDesc(UUID unitId);

    /**
     * Keyset page of leases in a given status, for {@link com.kenyarealestate.pms.service.RentInvoiceJob}.
     * Loading every ACTIVE lease platform-wide in one query does not survive growth - this
     * walks them {@code pageable.getPageSize()} at a time ordered by id, which stays correct
     * even if a lease's status changes between batches (unlike OFFSET paging, nothing already
     * scanned can shift back into view or get skipped).
     */
    List<Lease> findByStatusOrderByIdAsc(LeaseStatus status, Pageable pageable);
    List<Lease> findByStatusAndIdGreaterThanOrderByIdAsc(LeaseStatus status, UUID id, Pageable pageable);

    /**
     * One tenant's lease in a given state.
     *
     * <p>Replaces loading every lease in that state and filtering in memory. That worked
     * while a demo database held a few hundred rows and would have loaded every active
     * lease on the platform into one heap to answer a question about one tenant.
     * Served by {@code idx_leases_tenant}, narrowed further by {@code idx_leases_tenant_status}.
     */
    Optional<Lease> findFirstByTenantIdAndStatus(UUID tenantId, LeaseStatus status);
    boolean existsByTenantId(UUID tenantId);
    long countByLandlordIdAndStatus(UUID landlordId, LeaseStatus status);
}
