package com.soko.persistence;

import com.soko.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);
    @Query("select c from Customer c where c.tenantId = :tenantId and (lower(c.name) like :pattern escape '\\' "
            + "or lower(c.phone) like :pattern escape '\\' or lower(c.county) like :pattern escape '\\') "
            + "order by c.name, c.id")
    Page<Customer> search(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantId(UUID tenantId);

    // phone has no uniqueness constraint, so this is a List, not an Optional:
    // a derived findBy... query returning Optional throws at runtime the
    // moment more than one row matches. OtpService's verify flow always
    // requests a code before it can reach here, so two concurrent
    // registrations under the same brand-new number can't both pass
    // verification for the same code -- the race this guarded against for
    // the old guest checkout doesn't apply the same way here, but the return
    // type stays defensive regardless.
    List<Customer> findByTenantIdAndPhone(UUID tenantId, String phone);
}
