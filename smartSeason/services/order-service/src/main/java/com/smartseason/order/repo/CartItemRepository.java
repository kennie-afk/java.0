package com.smartseason.order.repo;

import com.smartseason.order.domain.CartItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<CartItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<CartItem> findAllByCartIdAndTenantId(UUID cartId, UUID tenantId, Pageable pageable);
    Page<CartItem> findAllByListingIdAndTenantId(UUID listingId, UUID tenantId, Pageable pageable);
}
