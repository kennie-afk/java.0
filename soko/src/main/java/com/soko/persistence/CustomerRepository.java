package com.soko.persistence;

import com.soko.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantId(UUID tenantId);

    // phone has no uniqueness constraint (nothing else in this codebase
    // enforces one either), so this is a List, not an Optional: a derived
    // findBy... query returning Optional throws at runtime the moment more
    // than one row matches, and two rows CAN exist here - two concurrent
    // guest checkouts under a brand-new phone number can both see "not
    // found" and both insert. Guest-checkout picks the first match; this is
    // the same low-stakes, unguarded-duplicate risk tolerance the owner's
    // own POST /v1/customers already accepts, not a new regression.
    List<Customer> findByTenantIdAndPhone(UUID tenantId, String phone);
}
