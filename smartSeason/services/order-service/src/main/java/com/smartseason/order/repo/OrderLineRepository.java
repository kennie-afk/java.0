package com.smartseason.order.repo;

import com.smartseason.order.domain.OrderLine;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {

    Optional<OrderLine> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<OrderLine> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<OrderLine> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
    Page<OrderLine> findAllByListingIdAndTenantId(UUID listingId, UUID tenantId, Pageable pageable);
}
