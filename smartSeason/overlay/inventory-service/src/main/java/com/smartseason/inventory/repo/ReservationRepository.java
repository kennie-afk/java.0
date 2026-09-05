package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Reservation> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Reservation> findAllByStockItemIdAndTenantId(UUID stockItemId, UUID tenantId, Pageable pageable);

    Page<Reservation> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);

    List<Reservation> findAllByStatusAndTenantIdAndExpiresAtBefore(
            Reservation.Status status, UUID tenantId, Instant before);
}
