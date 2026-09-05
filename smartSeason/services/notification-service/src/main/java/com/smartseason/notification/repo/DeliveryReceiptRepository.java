package com.smartseason.notification.repo;

import com.smartseason.notification.domain.DeliveryReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, UUID> {

    Optional<DeliveryReceipt> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<DeliveryReceipt> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<DeliveryReceipt> findAllByNotificationIdAndTenantId(UUID notificationId, UUID tenantId, Pageable pageable);
    Page<DeliveryReceipt> findAllByProviderRefAndTenantId(String providerRef, UUID tenantId, Pageable pageable);
}
