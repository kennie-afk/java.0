package com.smartseason.notification.repo;

import com.smartseason.notification.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Notification> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Notification> findAllByRecipientUserIdAndTenantId(UUID recipientUserId, UUID tenantId, Pageable pageable);
    Page<Notification> findAllByRecipientPhoneAndTenantId(String recipientPhone, UUID tenantId, Pageable pageable);
    Page<Notification> findAllByTemplateCodeAndTenantId(String templateCode, UUID tenantId, Pageable pageable);
    Optional<Notification> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
