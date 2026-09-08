package com.smartseason.notification.repo;

import com.smartseason.notification.domain.UssdSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UssdSessionRepository extends JpaRepository<UssdSession, UUID> {

    Optional<UssdSession> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<UssdSession> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UssdSession> findBySessionIdAndTenantId(String sessionId, UUID tenantId);
    Page<UssdSession> findAllByPhoneNumberAndTenantId(String phoneNumber, UUID tenantId, Pageable pageable);
}
