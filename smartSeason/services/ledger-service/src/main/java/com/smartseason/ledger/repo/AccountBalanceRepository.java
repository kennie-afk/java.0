package com.smartseason.ledger.repo;

import com.smartseason.ledger.domain.AccountBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    Optional<AccountBalance> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<AccountBalance> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AccountBalance> findByAccountIdAndTenantId(UUID accountId, UUID tenantId);

    Page<AccountBalance> findAllByAccountCodeAndTenantId(String accountCode, UUID tenantId, Pageable pageable);
}
