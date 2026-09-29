package com.soko.persistence;

import com.soko.domain.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByIssuedAtDesc(UUID tenantId, Pageable pageable);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
}
