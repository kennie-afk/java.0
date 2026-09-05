package com.smartseason.ledger.repo;

import com.smartseason.ledger.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Account> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Account> findByAccountCodeAndTenantId(String accountCode, UUID tenantId);

    Page<Account> findAllByOwnerOrgIdAndTenantId(UUID ownerOrgId, UUID tenantId, Pageable pageable);

    Page<Account> findAllByOwnerUserIdAndTenantId(UUID ownerUserId, UUID tenantId, Pageable pageable);
}
