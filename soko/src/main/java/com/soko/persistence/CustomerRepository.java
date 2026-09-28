package com.soko.persistence;

import com.soko.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);
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
