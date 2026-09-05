package com.smartseason.order.repo;

import com.smartseason.order.domain.OrderSagaState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSagaStateRepository extends JpaRepository<OrderSagaState, UUID> {

    Optional<OrderSagaState> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<OrderSagaState> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<OrderSagaState> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);
}
