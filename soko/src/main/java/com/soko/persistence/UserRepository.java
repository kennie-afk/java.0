package com.soko.persistence;

import com.soko.domain.AppUser;
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
public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailAndStatus(String email, String status);

    // A List, not an Optional: nothing stops a real customer account from
    // having more than one login (two staff members sharing an account), and
    // a findBy... derived query returning Optional throws at runtime the
    // moment more than one row matches.
    List<AppUser> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    List<AppUser> findByTenantIdAndRole(UUID tenantId, String role);

    @Query("select u from AppUser u where u.tenantId = :tenantId and (lower(u.email) like :pattern escape '\\' "
            + "or lower(u.fullName) like :pattern escape '\\') order by u.fullName, u.id")
    Page<AppUser> search(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);
}
