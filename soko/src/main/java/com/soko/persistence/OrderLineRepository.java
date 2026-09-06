package com.soko.persistence;

import com.soko.domain.OrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {
    List<OrderLine> findByOrderId(UUID orderId);
    List<OrderLine> findByTenantIdAndStatus(UUID tenantId, String status);
}
