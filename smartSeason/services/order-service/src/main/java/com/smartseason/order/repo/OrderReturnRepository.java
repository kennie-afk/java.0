package com.smartseason.order.repo;

import com.smartseason.order.domain.OrderReturn;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {

    Optional<OrderReturn> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<OrderReturn> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<OrderReturn> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
    Page<OrderReturn> findAllByOrderLineIdAndTenantId(UUID orderLineId, UUID tenantId, Pageable pageable);
}
