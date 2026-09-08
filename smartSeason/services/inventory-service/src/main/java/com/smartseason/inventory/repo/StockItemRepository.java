package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.StockItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<StockItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<StockItem> findAllByWarehouseIdAndTenantId(UUID warehouseId, UUID tenantId, Pageable pageable);
    Page<StockItem> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<StockItem> findAllByBatchIdAndTenantId(UUID batchId, UUID tenantId, Pageable pageable);
}
