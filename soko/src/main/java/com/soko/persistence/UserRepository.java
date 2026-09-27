package com.soko.persistence;

import com.soko.domain.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailAndStatus(String email, String status);

    // A List, not an Optional: nothing stops a real customer account from
    // having more than one login (two staff members sharing an account), and
    // a findBy... derived query returning Optional throws at runtime the
    // moment more than one row matches.
    List<AppUser> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    List<AppUser> findByTenantIdAndRole(UUID tenantId, String role);
}
