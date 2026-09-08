package com.smartseason.payment.repo;

import com.smartseason.payment.domain.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Wallet> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Wallet> findAllByOwnerOrgIdAndTenantId(UUID ownerOrgId, UUID tenantId, Pageable pageable);
    Page<Wallet> findAllByOwnerUserIdAndTenantId(UUID ownerUserId, UUID tenantId, Pageable pageable);
}
