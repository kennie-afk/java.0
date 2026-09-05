package com.smartseason.payment.repo;

import com.smartseason.payment.domain.PaymentIntent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PaymentIntent> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PaymentIntent> findByReferenceAndTenantId(String reference, UUID tenantId);

    Optional<PaymentIntent> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);

    Page<PaymentIntent> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);

    Page<PaymentIntent> findAllByPayerOrgIdAndTenantId(UUID payerOrgId, UUID tenantId, Pageable pageable);

    Page<PaymentIntent> findAllByPayeeOrgIdAndTenantId(UUID payeeOrgId, UUID tenantId, Pageable pageable);

    Page<PaymentIntent> findAllByProviderRefAndTenantId(String providerRef, UUID tenantId, Pageable pageable);
}
